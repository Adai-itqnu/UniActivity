/**
 * UniActivity Admin Panel JavaScript Utilities
 * Provides AJAX helpers, Modal management, Toast notifications
 */

// ========================================
// Toast Notification System
// ========================================
const Toast = {
    container: null,

    init() {
        if (!this.container) {
            this.container = document.createElement('div');
            this.container.className = 'toast-container';
            document.body.appendChild(this.container);
        }
    },

    show(message, type = 'info', duration = 4000) {
        this.init();
        
        const toast = document.createElement('div');
        toast.className = `toast toast-${type} show`;
        toast.setAttribute('role', 'alert');
        
        const icons = {
            success: 'fas fa-check-circle',
            error: 'fas fa-exclamation-circle',
            warning: 'fas fa-exclamation-triangle',
            info: 'fas fa-info-circle'
        };
        
        toast.innerHTML = `
            <div class="toast-body d-flex align-items-center">
                <i class="${icons[type]} me-2"></i>
                <span>${message}</span>
                <button type="button" class="btn-close btn-close-white ms-auto" onclick="this.parentElement.parentElement.remove()"></button>
            </div>
        `;
        
        this.container.appendChild(toast);
        
        setTimeout(() => {
            toast.style.animation = 'slideOutRight 0.3s ease forwards';
            setTimeout(() => toast.remove(), 300);
        }, duration);
    },

    success(message) { this.show(message, 'success'); },
    error(message) { this.show(message, 'error', 6000); },
    warning(message) { this.show(message, 'warning'); },
    info(message) { this.show(message, 'info'); }
};

// Add slideOutRight animation
const style = document.createElement('style');
style.textContent = `
    @keyframes slideOutRight {
        from { transform: translateX(0); opacity: 1; }
        to { transform: translateX(100%); opacity: 0; }
    }
`;
document.head.appendChild(style);


// ========================================
// Loading Overlay
// ========================================
const Loading = {
    overlay: null,

    show() {
        if (!this.overlay) {
            this.overlay = document.createElement('div');
            this.overlay.className = 'loading-overlay';
            this.overlay.innerHTML = `
                <div class="text-center">
                    <div class="spinner-border text-primary" role="status">
                        <span class="visually-hidden">Loading...</span>
                    </div>
                    <p class="mt-2 text-muted">Đang xử lý...</p>
                </div>
            `;
        }
        document.body.appendChild(this.overlay);
    },

    hide() {
        if (this.overlay && this.overlay.parentNode) {
            this.overlay.remove();
        }
    }
};


// ========================================
// AJAX Helper
// ========================================
const API = {
    baseUrl: '',

    async request(url, options = {}) {
        const defaultOptions = {
            headers: {
                'Content-Type': 'application/json',
            },
        };
        
        const mergedOptions = { ...defaultOptions, ...options };
        
        if (options.body && typeof options.body === 'object') {
            mergedOptions.body = JSON.stringify(options.body);
        }

        try {
            const response = await fetch(this.baseUrl + url, mergedOptions);
            
            if (!response.ok) {
                const errorData = await response.json().catch(() => ({}));
                throw new Error(errorData.message || `HTTP Error: ${response.status}`);
            }
            
            const contentType = response.headers.get('content-type');
            if (contentType && contentType.includes('application/json')) {
                return await response.json();
            }
            
            return await response.text();
        } catch (error) {
            console.error('API Error:', error);
            throw error;
        }
    },

    get(url) {
        return this.request(url, { method: 'GET' });
    },

    post(url, data) {
        return this.request(url, { method: 'POST', body: data });
    },

    put(url, data) {
        return this.request(url, { method: 'PUT', body: data });
    },

    delete(url) {
        return this.request(url, { method: 'DELETE' });
    }
};


// ========================================
// Modal Manager
// ========================================
const Modal = {
    current: null,

    open(modalId) {
        const modalElement = document.getElementById(modalId);
        if (modalElement) {
            this.current = new bootstrap.Modal(modalElement);
            this.current.show();
        }
    },

    close(modalId) {
        const modalElement = document.getElementById(modalId);
        if (modalElement) {
            const modalInstance = bootstrap.Modal.getInstance(modalElement);
            if (modalInstance) {
                modalInstance.hide();
            }
        }
    },

    reset(formId) {
        const form = document.getElementById(formId);
        if (form) {
            form.reset();
            // Clear any validation states
            form.querySelectorAll('.is-invalid').forEach(el => el.classList.remove('is-invalid'));
            form.querySelectorAll('.invalid-feedback').forEach(el => el.textContent = '');
            // Reset hidden fields
            form.querySelectorAll('input[type="hidden"]').forEach(el => {
                if (el.name === 'id') el.value = '';
            });
        }
    }
};


// ========================================
// Form Utilities
// ========================================
const FormUtils = {
    serialize(formElement) {
        const formData = new FormData(formElement);
        const data = {};
        
        for (let [key, value] of formData.entries()) {
            // Handle nested properties (e.g., faculty.id)
            if (key.includes('.')) {
                const keys = key.split('.');
                let current = data;
                for (let i = 0; i < keys.length - 1; i++) {
                    if (!current[keys[i]]) current[keys[i]] = {};
                    current = current[keys[i]];
                }
                current[keys[keys.length - 1]] = value;
            } else {
                data[key] = value;
            }
        }
        
        return data;
    },

    populate(formElement, data) {
        if (!formElement || !data) return;
        
        Object.keys(data).forEach(key => {
            const field = formElement.querySelector(`[name="${key}"]`);
            if (field) {
                if (field.type === 'checkbox') {
                    field.checked = Boolean(data[key]);
                } else if (field.type === 'select-one' && typeof data[key] === 'object') {
                    // Handle relations (e.g., faculty: {id: 1})
                    field.value = data[key]?.id || '';
                } else {
                    field.value = data[key] || '';
                }
            }
            
            // Handle nested objects for select fields
            if (typeof data[key] === 'object' && data[key]?.id) {
                const nestedField = formElement.querySelector(`[name="${key}.id"]`);
                if (nestedField) {
                    nestedField.value = data[key].id;
                }
            }
        });
    },

    validate(formElement) {
        let isValid = true;
        
        formElement.querySelectorAll('[required]').forEach(field => {
            if (!field.value.trim()) {
                field.classList.add('is-invalid');
                isValid = false;
            } else {
                field.classList.remove('is-invalid');
            }
        });
        
        return isValid;
    }
};


// ========================================
// Table Utilities
// ========================================
const TableUtils = {
    refresh(tableId, data, rowTemplate) {
        const tbody = document.querySelector(`#${tableId} tbody`);
        if (!tbody) return;
        
        if (data.length === 0) {
            tbody.innerHTML = `
                <tr>
                    <td colspan="100" class="text-center py-4">
                        <div class="empty-state">
                            <i class="fas fa-inbox"></i>
                            <h5>Không có dữ liệu</h5>
                            <p>Chưa có bản ghi nào được tạo.</p>
                        </div>
                    </td>
                </tr>
            `;
            return;
        }
        
        tbody.innerHTML = data.map(rowTemplate).join('');
    }
};


// ========================================
// Confirm Dialog
// ========================================
const Confirm = {
    show(options) {
        return new Promise((resolve) => {
            const { title = 'Xác nhận', message, confirmText = 'Xác nhận', cancelText = 'Hủy', type = 'danger' } = options;
            
            const modalHtml = `
                <div class="modal fade" id="confirmModal" tabindex="-1">
                    <div class="modal-dialog modal-dialog-centered">
                        <div class="modal-content">
                            <div class="modal-header">
                                <h5 class="modal-title">${title}</h5>
                                <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
                            </div>
                            <div class="modal-body">
                                <p>${message}</p>
                            </div>
                            <div class="modal-footer">
                                <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">${cancelText}</button>
                                <button type="button" class="btn btn-${type}" id="confirmBtn">${confirmText}</button>
                            </div>
                        </div>
                    </div>
                </div>
            `;
            
            // Remove existing modal if any
            const existing = document.getElementById('confirmModal');
            if (existing) existing.remove();
            
            document.body.insertAdjacentHTML('beforeend', modalHtml);
            
            const modalElement = document.getElementById('confirmModal');
            const modal = new bootstrap.Modal(modalElement);
            
            document.getElementById('confirmBtn').addEventListener('click', () => {
                modal.hide();
                resolve(true);
            });
            
            modalElement.addEventListener('hidden.bs.modal', () => {
                modalElement.remove();
                resolve(false);
            });
            
            modal.show();
        });
    },

    delete(itemName) {
        return this.show({
            title: 'Xác nhận xóa',
            message: `Bạn có chắc chắn muốn xóa <strong>${itemName}</strong>? Hành động này không thể hoàn tác.`,
            confirmText: 'Xóa',
            type: 'danger'
        });
    }
};


// ========================================
// CRUD Controller Base
// ========================================
class CrudController {
    constructor(options) {
        this.apiEndpoint = options.apiEndpoint;
        this.tableId = options.tableId;
        this.formId = options.formId;
        this.modalId = options.modalId;
        this.entityName = options.entityName;
        this.rowTemplate = options.rowTemplate;
        this.onAfterLoad = options.onAfterLoad || (() => {});
        this.onBeforeSave = options.onBeforeSave || ((data) => data);
    }

    async loadData() {
        try {
            Loading.show();
            const data = await API.get(this.apiEndpoint);
            TableUtils.refresh(this.tableId, data, this.rowTemplate);
            this.onAfterLoad(data);
        } catch (error) {
            Toast.error('Không thể tải dữ liệu: ' + error.message);
        } finally {
            Loading.hide();
        }
    }

    openCreateModal() {
        Modal.reset(this.formId);
        document.querySelector(`#${this.modalId} .modal-title`).textContent = `Thêm mới ${this.entityName}`;
        Modal.open(this.modalId);
    }

    async openEditModal(id) {
        try {
            Loading.show();
            const data = await API.get(`${this.apiEndpoint}/${id}`);
            Modal.reset(this.formId);
            FormUtils.populate(document.getElementById(this.formId), data);
            document.getElementById(this.formId).querySelector('[name="id"]').value = id;
            document.querySelector(`#${this.modalId} .modal-title`).textContent = `Cập nhật ${this.entityName}`;
            Modal.open(this.modalId);
        } catch (error) {
            Toast.error('Không thể tải dữ liệu: ' + error.message);
        } finally {
            Loading.hide();
        }
    }

    async save() {
        const form = document.getElementById(this.formId);
        
        if (!FormUtils.validate(form)) {
            Toast.warning('Vui lòng điền đầy đủ thông tin bắt buộc.');
            return;
        }

        let data = FormUtils.serialize(form);
        data = this.onBeforeSave(data);
        
        const id = data.id;
        delete data.id;

        try {
            Loading.show();
            
            if (id) {
                await API.put(`${this.apiEndpoint}/${id}`, data);
                Toast.success(`Cập nhật ${this.entityName} thành công!`);
            } else {
                await API.post(this.apiEndpoint, data);
                Toast.success(`Thêm mới ${this.entityName} thành công!`);
            }
            
            Modal.close(this.modalId);
            await this.loadData();
        } catch (error) {
            Toast.error('Lỗi: ' + error.message);
        } finally {
            Loading.hide();
        }
    }

    async delete(id, name) {
        const confirmed = await Confirm.delete(name);
        if (!confirmed) return;

        try {
            Loading.show();
            await API.delete(`${this.apiEndpoint}/${id}`);
            Toast.success(`Xóa ${this.entityName} thành công!`);
            await this.loadData();
        } catch (error) {
            Toast.error('Không thể xóa: ' + error.message);
        } finally {
            Loading.hide();
        }
    }
}


// ========================================
// Document Ready
// ========================================
document.addEventListener('DOMContentLoaded', function() {
    // Initialize tooltips
    const tooltipTriggerList = [].slice.call(document.querySelectorAll('[data-bs-toggle="tooltip"]'));
    tooltipTriggerList.map(function (tooltipTriggerEl) {
        return new bootstrap.Tooltip(tooltipTriggerEl);
    });

    // Active sidebar link
    const currentPath = window.location.pathname;
    document.querySelectorAll('.sidebar a').forEach(link => {
        if (link.getAttribute('href') === currentPath) {
            link.classList.add('active');
        }
    });
});
