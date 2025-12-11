package com.example.uniactivity.service;

import com.example.uniactivity.entity.PointRequest;
import com.example.uniactivity.entity.StudentClass;
import com.example.uniactivity.entity.User;
import com.example.uniactivity.enums.EvidenceStatus;
import com.example.uniactivity.repository.PointRequestRepository;
import com.example.uniactivity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final UserRepository userRepository;
    private final PointRequestRepository pointRequestRepository;

    /**
     * Generate Excel report of class members
     */
    public byte[] generateClassMembersReport(StudentClass studentClass) throws IOException {
        List<User> members = userRepository.findByStudentClass(studentClass);

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Danh sách thành viên");

            // Header style
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Font headerFont = workbook.createFont();
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            // Create header row
            Row headerRow = sheet.createRow(0);
            String[] headers = {"STT", "MSSV", "Họ và tên", "Email", "Số điện thoại", "Vai trò"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Fill data
            int rowNum = 1;
            for (User member : members) {
                Row row = sheet.createRow(rowNum);
                row.createCell(0).setCellValue(rowNum);
                row.createCell(1).setCellValue(member.getUsername());
                row.createCell(2).setCellValue(member.getFullName());
                row.createCell(3).setCellValue(member.getEmail());
                row.createCell(4).setCellValue(member.getPhone() != null ? member.getPhone() : "");
                row.createCell(5).setCellValue(member.getRole().name());
                rowNum++;
            }

            // Auto-size columns
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    /**
     * Generate Excel report of point requests for a class
     */
    public byte[] generatePointRequestsReport(StudentClass studentClass) throws IOException {
        List<PointRequest> requests = pointRequestRepository.findByStudentClass(studentClass);

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Yêu cầu điểm");

            // Header style
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.DARK_GREEN.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Font headerFont = workbook.createFont();
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            // Create header row
            Row headerRow = sheet.createRow(0);
            String[] headers = {"STT", "MSSV", "Họ và tên", "Mục điểm", "Điểm yêu cầu", "Mô tả", "Trạng thái", "Ngày tạo", "Người duyệt", "Ghi chú"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

            // Fill data
            int rowNum = 1;
            for (PointRequest req : requests) {
                Row row = sheet.createRow(rowNum);
                row.createCell(0).setCellValue(rowNum);
                row.createCell(1).setCellValue(req.getStudent().getUsername());
                row.createCell(2).setCellValue(req.getStudent().getFullName());
                row.createCell(3).setCellValue(req.getCriteriaCode());
                row.createCell(4).setCellValue(req.getClaimedScore() != null ? req.getClaimedScore() : 0);
                row.createCell(5).setCellValue(req.getDescription() != null ? req.getDescription() : "");
                row.createCell(6).setCellValue(getStatusText(req.getStatus()));
                row.createCell(7).setCellValue(req.getCreatedAt() != null ? req.getCreatedAt().format(fmt) : "");
                row.createCell(8).setCellValue(req.getReviewer() != null ? req.getReviewer().getFullName() : "");
                row.createCell(9).setCellValue(req.getReviewComment() != null ? req.getReviewComment() : "");
                rowNum++;
            }

            // Auto-size columns
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private String getStatusText(EvidenceStatus status) {
        if (status == null) return "";
        switch (status) {
            case PENDING: return "Chờ duyệt";
            case APPROVED: return "Đã duyệt";
            case REJECTED: return "Từ chối";
            default: return status.name();
        }
    }
}
