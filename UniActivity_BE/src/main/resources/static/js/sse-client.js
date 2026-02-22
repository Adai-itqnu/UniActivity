/**
 * SSE Client - Quản lý kết nối Server-Sent Events
 * 
 * Chức năng chính:
 * 1. Kết nối SSE endpoint để nhận real-time events
 * 2. Exponential backoff khi reconnect (tránh spam server)
 * 3. Auto-reload trang khi nhận notification thay đổi trạng thái
 * 4. Sync badge thông báo giữa các tab (BroadcastChannel)
 * 5. Cập nhật dashboard stats real-time (không cần reload)
 */
(function () {
  "use strict";

  // ==================== Biến trạng thái ====================
  let eventSource = null;
  let reconnectTimer = null;
  let isConnected = false;

  // Exponential backoff: 3s → 6s → 12s → 24s → 48s → max 60s
  const BASE_RECONNECT_DELAY = 3000;
  const MAX_RECONNECT_DELAY = 60000;
  let currentReconnectDelay = BASE_RECONNECT_DELAY;

  // Custom event listeners cho các module khác đăng ký
  const eventListeners = {};

  // ==================== BroadcastChannel ====================
  // Sync thông báo giữa các tab CÙNG browser context
  // Lưu ý: Tab ẩn danh (incognito) có context riêng, không sync được
  let broadcastChannel = null;
  try {
    broadcastChannel = new BroadcastChannel("sse_notification_channel");
    broadcastChannel.onmessage = function (event) {
      if (event.data.type === "badge_update") {
        updateBadge(event.data.count);
      }
    };
  } catch (e) {
    console.warn("[SSE] BroadcastChannel not supported:", e.message);
  }

  // ==================== Danh sách notification types cần reload ====================
  /**
   * Khi nhận các loại notification này, trang sẽ TỰ ĐỘNG RELOAD
   * để cập nhật trạng thái UI (không chỉ hiện toast)
   * 
   * Ví dụ: Manager duyệt điểm → Student nhận POINT_REQUEST_APPROVED
   *        → trang điểm tự reload → hiển thị điểm mới
   */
  const RELOAD_NOTIFICATION_TYPES = [
    // === Student nhận (thay đổi trạng thái quan trọng) ===
    "JOIN_REQUEST_APPROVED",    // Được duyệt vào lớp → reload home/my-class
    "JOIN_REQUEST_REJECTED",    // Bị từ chối → reload home
    "REMOVED_FROM_CLASS",       // Bị xóa khỏi lớp → reload home
    "POINT_REQUEST_APPROVED",   // Điểm được duyệt → reload trang điểm
    "POINT_REQUEST_REJECTED",   // Điểm bị từ chối → reload trang điểm
    "EVIDENCE_APPROVED",        // Minh chứng được duyệt → reload
    "EVIDENCE_REJECTED",        // Minh chứng bị từ chối → reload
    "NEW_ACTIVITY",             // Có hoạt động mới → reload danh sách HĐ

    // === Manager nhận (có yêu cầu mới cần xử lý) ===
    "JOIN_REQUEST_SUBMITTED",   // Sinh viên gửi yêu cầu tham gia
    "POINT_REQUEST_SUBMITTED",  // Sinh viên gửi yêu cầu điểm
    "EVIDENCE_SUBMITTED",       // Sinh viên nộp minh chứng
    "STUDENT_CHECKED_IN",       // Sinh viên check-in
  ];

  // ==================== Kết nối SSE ====================
  /**
   * Kết nối đến SSE endpoint /sse/subscribe
   * Tự động xử lý: connected, heartbeat, notification, dashboard_update
   */
  function connect() {
    // Đóng kết nối cũ nếu có
    if (eventSource) {
      eventSource.close();
    }

    console.log("[SSE] Connecting to /sse/subscribe...");
    eventSource = new EventSource("/sse/subscribe");

    // === Event: Kết nối thành công ===
    eventSource.addEventListener("connected", function (event) {
      isConnected = true;
      // Reset backoff delay khi kết nối thành công
      currentReconnectDelay = BASE_RECONNECT_DELAY;
      console.log("[SSE] Connected:", event.data);

      // Load số thông báo chưa đọc ban đầu
      loadUnreadCount();
      console.log("[SSE] Real-time notification connected successfully!");
    });

    // === Event: Heartbeat (giữ kết nối sống) ===
    eventSource.addEventListener("heartbeat", function (event) {
      console.debug(
        "[SSE] Heartbeat received:",
        new Date(parseInt(event.data))
      );
    });

    // === Event: Notification (thông báo mới) ===
    // Đây là event chính xử lý tất cả thay đổi trạng thái
    eventSource.addEventListener("notification", function (event) {
      try {
        const data = JSON.parse(event.data);
        console.log("[SSE] Notification received:", data);
        console.log("[SSE] Notification type:", data.type);

        // Cập nhật badge số thông báo chưa đọc
        loadUnreadCount();

        // Trigger custom event listeners (cho các module khác lắng nghe)
        triggerEvent("notification", data);

        // Hiển thị toast notification cho user
        showNotificationToast(data);

        // Nếu notification thuộc loại cần cập nhật trạng thái → auto reload
        // User kịp đọc toast 2 giây rồi trang mới reload
        if (RELOAD_NOTIFICATION_TYPES.includes(data.type)) {
          console.log(
            "[SSE] State-changing notification (" +
              data.type +
              ") - reloading page in 2 seconds..."
          );
          setTimeout(function () {
            window.location.reload();
          }, 2000);
        }

        // Broadcast đến các tab khác (cùng browser context)
        if (broadcastChannel) {
          broadcastChannel.postMessage({
            type: "new_notification",
            data: data,
          });
        }
      } catch (e) {
        console.error("[SSE] Error parsing notification:", e);
      }
    });

    // === Event: Dashboard Update (cập nhật số liệu thống kê) ===
    // Server gửi khi có thay đổi: duyệt tham gia, duyệt điểm, xóa thành viên
    // Cập nhật trực tiếp stat cards trên dashboard mà KHÔNG cần reload toàn trang
    eventSource.addEventListener("dashboard_update", function (event) {
      try {
        const data = JSON.parse(event.data);
        console.log("[SSE] Dashboard update received:", data);

        // Cập nhật các stat cards nếu đang ở trang dashboard
        updateDashboardStats(data);

        // Trigger custom event listeners
        triggerEvent("dashboard_update", data);
      } catch (e) {
        console.error("[SSE] Error parsing dashboard update:", e);
      }
    });

    // === Xử lý lỗi kết nối ===
    // Dùng exponential backoff để tránh spam server khi server down
    eventSource.onerror = function (error) {
      console.error("[SSE] Connection error:", error);
      isConnected = false;
      eventSource.close();

      // Clear timer cũ nếu có
      if (reconnectTimer) {
        clearTimeout(reconnectTimer);
      }

      // Exponential backoff: delay tăng gấp đôi mỗi lần, tối đa 60s
      console.log(
        "[SSE] Reconnecting in " + currentReconnectDelay / 1000 + "s..."
      );
      reconnectTimer = setTimeout(function () {
        console.log("[SSE] Attempting to reconnect...");
        connect();
      }, currentReconnectDelay);

      // Tăng delay cho lần reconnect tiếp theo
      currentReconnectDelay = Math.min(
        currentReconnectDelay * 2,
        MAX_RECONNECT_DELAY
      );
    };
  }

  // ==================== Dashboard Stats Update ====================
  /**
   * Cập nhật trực tiếp các stat cards trên trang dashboard
   * Không cần reload toàn trang, chỉ thay đổi số liệu
   * 
   * @param {Object} data - Chứa các key: memberCount, pendingJoinRequests, 
   *                        pendingPointRequests, avgTrainingPoints
   */
  function updateDashboardStats(data) {
    // Chỉ cập nhật nếu đang ở trang dashboard (manager hoặc admin)
    const path = window.location.pathname;
    if (!path.includes("/dashboard")) {
      return;
    }

    // Tìm và cập nhật các thẻ stat card theo nội dung label
    const statCards = document.querySelectorAll(".stat-card-new .card-body");
    statCards.forEach(function (card) {
      const label = card.querySelector("small");
      const value = card.querySelector("h3");
      if (!label || !value) return;

      const labelText = label.textContent.trim();

      // Map label text → data key
      if (labelText === "Thành viên lớp" && data.memberCount !== undefined) {
        value.textContent = data.memberCount;
      } else if (
        labelText === "Yêu cầu tham gia" &&
        data.pendingJoinRequests !== undefined
      ) {
        value.textContent = data.pendingJoinRequests;
      } else if (
        labelText === "Yêu cầu điểm" &&
        data.pendingPointRequests !== undefined
      ) {
        value.textContent = data.pendingPointRequests;
      } else if (
        labelText === "Điểm TB lớp" &&
        data.avgTrainingPoints !== undefined
      ) {
        value.textContent = data.avgTrainingPoints;
      }
    });
  }

  // ==================== Badge & Toast ====================

  /**
   * Load số thông báo chưa đọc từ API
   * Tự detect role (student/manager) từ URL path
   */
  function loadUnreadCount() {
    const path = window.location.pathname;
    let apiUrl = null;

    if (path.startsWith("/student")) {
      apiUrl = "/student/api/notifications/unread-count";
    } else if (path.startsWith("/manager")) {
      apiUrl = "/manager/api/notifications/unread-count";
    }

    if (!apiUrl) return;

    fetch(apiUrl)
      .then(function (res) {
        return res.json();
      })
      .then(function (data) {
        updateBadge(data.count);
        // Broadcast số badge đến các tab khác
        if (broadcastChannel) {
          broadcastChannel.postMessage({
            type: "badge_update",
            count: data.count,
          });
        }
      })
      .catch(function (err) {
        console.error("[SSE] Error loading unread count:", err);
      });
  }

  /**
   * Cập nhật badge số thông báo trên thanh navigation
   * @param {number} count - Số thông báo chưa đọc
   */
  function updateBadge(count) {
    const badge = document.getElementById("notificationBadge");
    if (badge) {
      if (count > 0) {
        badge.textContent = count > 99 ? "99+" : count;
        badge.classList.remove("d-none");
      } else {
        badge.classList.add("d-none");
      }
    }
  }

  /**
   * Hiển thị toast notification (Bootstrap Toast)
   * Tự tạo container nếu chưa có
   * @param {Object} data - { title, message, link }
   */
  function showNotificationToast(data) {
    const toastContainer =
      document.getElementById("toastContainer") || createToastContainer();

    const toastId = "toast-" + Date.now();
    const toastHtml = `
      <div id="${toastId}" class="toast" role="alert" aria-live="assertive" aria-atomic="true">
        <div class="toast-header">
          <i class="fas fa-bell text-primary me-2"></i>
          <strong class="me-auto">${data.title || "Thông báo mới"}</strong>
          <small>Vừa xong</small>
          <button type="button" class="btn-close" data-bs-dismiss="toast"></button>
        </div>
        <div class="toast-body">
          ${data.message || ""}
          ${
            data.link
              ? `<div class="mt-2"><a href="${data.link}" class="btn btn-sm btn-primary">Xem chi tiết</a></div>`
              : ""
          }
        </div>
      </div>
    `;

    toastContainer.insertAdjacentHTML("beforeend", toastHtml);

    const toastEl = document.getElementById(toastId);
    const toast = new bootstrap.Toast(toastEl, { delay: 5000 });
    toast.show();

    // Dọn dẹp: xóa toast element sau khi hidden
    toastEl.addEventListener("hidden.bs.toast", function () {
      toastEl.remove();
    });
  }

  /**
   * Tạo container cho toasts (góc trên phải màn hình)
   */
  function createToastContainer() {
    const container = document.createElement("div");
    container.id = "toastContainer";
    container.className = "toast-container position-fixed top-0 end-0 p-3";
    container.style.zIndex = "1100";
    document.body.appendChild(container);
    return container;
  }

  // ==================== Custom Event System ====================

  /**
   * Đăng ký custom event listener
   * Cho phép các module/page khác lắng nghe SSE events
   * Ví dụ: SSE.on('notification', function(data) { ... });
   */
  function on(eventName, callback) {
    if (!eventListeners[eventName]) {
      eventListeners[eventName] = [];
    }
    eventListeners[eventName].push(callback);
  }

  /**
   * Hủy đăng ký event listener
   */
  function off(eventName, callback) {
    if (eventListeners[eventName]) {
      eventListeners[eventName] = eventListeners[eventName].filter(
        function (cb) {
          return cb !== callback;
        }
      );
    }
  }

  /**
   * Trigger tất cả listeners cho một event
   */
  function triggerEvent(eventName, data) {
    if (eventListeners[eventName]) {
      eventListeners[eventName].forEach(function (callback) {
        try {
          callback(data);
        } catch (e) {
          console.error("[SSE] Error in event listener:", e);
        }
      });
    }
  }

  // ==================== Connection Management ====================

  /**
   * Ngắt kết nối SSE và dọn dẹp timers
   */
  function disconnect() {
    if (eventSource) {
      eventSource.close();
      eventSource = null;
    }
    if (reconnectTimer) {
      clearTimeout(reconnectTimer);
      reconnectTimer = null;
    }
    isConnected = false;
    // Reset backoff delay
    currentReconnectDelay = BASE_RECONNECT_DELAY;
    console.log("[SSE] Disconnected");
  }

  /**
   * Kiểm tra trạng thái kết nối hiện tại
   * @returns {{ isConnected: boolean, readyState: number }}
   */
  function getConnectionStatus() {
    return {
      isConnected: isConnected,
      readyState: eventSource ? eventSource.readyState : EventSource.CLOSED,
    };
  }

  // ==================== Tự động kết nối ====================

  // Kết nối SSE ngay khi script được load
  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", connect);
  } else {
    connect();
  }

  // Ngắt kết nối khi rời trang (tránh connection leak)
  window.addEventListener("beforeunload", disconnect);

  // ==================== Export API ====================
  // Cho phép các trang khác tương tác với SSE client
  // Sử dụng: SSE.getStatus(), SSE.on('notification', handler), etc.
  window.SSE = {
    connect: connect,
    disconnect: disconnect,
    getStatus: getConnectionStatus,
    on: on,
    off: off,
    loadUnreadCount: loadUnreadCount,
  };

  console.log(
    "[SSE] Client initialized. Use SSE.getStatus() to check connection."
  );
})();
