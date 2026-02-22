package com.example.uniactivity.service;

import com.example.uniactivity.entity.PointRequest;
import com.example.uniactivity.entity.Semester;
import com.example.uniactivity.entity.StudentClass;
import com.example.uniactivity.entity.StudentTrainingPoint;
import com.example.uniactivity.entity.TrainingPointDetail;
import com.example.uniactivity.entity.User;
import com.example.uniactivity.enums.EvidenceStatus;
import com.example.uniactivity.repository.PointRequestRepository;
import com.example.uniactivity.repository.SemesterRepository;
import com.example.uniactivity.repository.StudentTrainingPointRepository;
import com.example.uniactivity.repository.TrainingPointDetailRepository;
import com.example.uniactivity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final UserRepository userRepository;
    private final PointRequestRepository pointRequestRepository;
    private final StudentTrainingPointRepository studentTrainingPointRepository;
    private final TrainingPointDetailRepository trainingPointDetailRepository;
    private final SemesterRepository semesterRepository;

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
    /**
     * Generate comprehensive Excel report of student training points by criteria categories
     * Format: STT | MSSV | Họ tên | 1.1 | 1.2 | 1.3 | 1.4 | Tổng M1 | 2.1 | ... | Tổng | Max | Xếp loại | Ghi chú
     */
    public byte[] generateStudentPointsSummaryReport(StudentClass studentClass) throws IOException {
        List<User> members = userRepository.findByStudentClass(studentClass);
        Semester currentSemester = semesterRepository.findByIsCurrentTrue();
        
        if (currentSemester == null) {
            throw new RuntimeException("Không tìm thấy học kỳ hiện tại");
        }

        // Define all criteria codes for 6 main categories
        String[][] criteriaGroups = {
            {"1.1", "1.2", "1.3", "1.4"}, // Mục 1: Ý thức học tập
            {"2.1", "2.2", "2.3"},         // Mục 2: Chấp hành nội quy
            {"3.1", "3.2", "3.3"},         // Mục 3: Hoạt động chính trị
            {"4.1", "4.2", "4.3"},         // Mục 4: Phẩm chất công dân
            {"5.1", "5.2", "5.3"},         // Mục 5: Hoạt động lớp
            {"6.1"}                        // Mục 6: Thành tích đặc biệt
        };
        
        String[] groupNames = {
            "1. Ý thức học tập",
            "2. Chấp hành nội quy, quy chế",
            "3. Hoạt động chính trị - xã hội",
            "4. Phẩm chất công dân",
            "5. Hoạt động lớp, đoàn thể",
            "6. Thành tích đặc biệt"
        };
        
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Điểm rèn luyện");

            // Styles - Header (dark blue)
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);
            Font headerFont = workbook.createFont();
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            // Sub header style (light blue)
            CellStyle subHeaderStyle = workbook.createCellStyle();
            subHeaderStyle.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
            subHeaderStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            subHeaderStyle.setAlignment(HorizontalAlignment.CENTER);
            subHeaderStyle.setBorderBottom(BorderStyle.THIN);
            subHeaderStyle.setBorderTop(BorderStyle.THIN);
            subHeaderStyle.setBorderLeft(BorderStyle.THIN);
            subHeaderStyle.setBorderRight(BorderStyle.THIN);
            Font subFont = workbook.createFont();
            subFont.setBold(true);
            subHeaderStyle.setFont(subFont);

            // Category total header style (green)
            CellStyle categoryTotalHeaderStyle = workbook.createCellStyle();
            categoryTotalHeaderStyle.setFillForegroundColor(IndexedColors.BRIGHT_GREEN.getIndex());
            categoryTotalHeaderStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            categoryTotalHeaderStyle.setAlignment(HorizontalAlignment.CENTER);
            categoryTotalHeaderStyle.setBorderBottom(BorderStyle.THIN);
            categoryTotalHeaderStyle.setBorderTop(BorderStyle.THIN);
            categoryTotalHeaderStyle.setBorderLeft(BorderStyle.THIN);
            categoryTotalHeaderStyle.setBorderRight(BorderStyle.THIN);
            Font catTotalFont = workbook.createFont();
            catTotalFont.setBold(true);
            categoryTotalHeaderStyle.setFont(catTotalFont);

            // Grand total style (yellow/gold)
            CellStyle grandTotalStyle = workbook.createCellStyle();
            grandTotalStyle.setFillForegroundColor(IndexedColors.GOLD.getIndex());
            grandTotalStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            grandTotalStyle.setAlignment(HorizontalAlignment.CENTER);
            grandTotalStyle.setBorderBottom(BorderStyle.THIN);
            grandTotalStyle.setBorderTop(BorderStyle.THIN);
            grandTotalStyle.setBorderLeft(BorderStyle.THIN);
            grandTotalStyle.setBorderRight(BorderStyle.THIN);
            Font grandFont = workbook.createFont();
            grandFont.setBold(true);
            grandTotalStyle.setFont(grandFont);

            // Data style
            CellStyle dataStyle = workbook.createCellStyle();
            dataStyle.setBorderBottom(BorderStyle.THIN);
            dataStyle.setBorderTop(BorderStyle.THIN);
            dataStyle.setBorderLeft(BorderStyle.THIN);
            dataStyle.setBorderRight(BorderStyle.THIN);

            CellStyle centerDataStyle = workbook.createCellStyle();
            centerDataStyle.cloneStyleFrom(dataStyle);
            centerDataStyle.setAlignment(HorizontalAlignment.CENTER);

            // Category total data style (light green background)
            CellStyle categoryTotalDataStyle = workbook.createCellStyle();
            categoryTotalDataStyle.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
            categoryTotalDataStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            categoryTotalDataStyle.setAlignment(HorizontalAlignment.CENTER);
            categoryTotalDataStyle.setBorderBottom(BorderStyle.THIN);
            categoryTotalDataStyle.setBorderTop(BorderStyle.THIN);
            categoryTotalDataStyle.setBorderLeft(BorderStyle.THIN);
            categoryTotalDataStyle.setBorderRight(BorderStyle.THIN);
            Font catDataFont = workbook.createFont();
            catDataFont.setBold(true);
            categoryTotalDataStyle.setFont(catDataFont);

            // Grand total data style (light yellow)
            CellStyle grandTotalDataStyle = workbook.createCellStyle();
            grandTotalDataStyle.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
            grandTotalDataStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            grandTotalDataStyle.setAlignment(HorizontalAlignment.CENTER);
            grandTotalDataStyle.setBorderBottom(BorderStyle.THIN);
            grandTotalDataStyle.setBorderTop(BorderStyle.THIN);
            grandTotalDataStyle.setBorderLeft(BorderStyle.THIN);
            grandTotalDataStyle.setBorderRight(BorderStyle.THIN);
            Font grandDataFont = workbook.createFont();
            grandDataFont.setBold(true);
            grandTotalDataStyle.setFont(grandDataFont);

            // Row 0: Class info
            Row titleRow = sheet.createRow(0);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("Lớp: " + studentClass.getName() + " - HK: " + currentSemester.getName());

            // Row 1: Main headers
            Row mainHeaderRow = sheet.createRow(1);
            int col = 0;
            
            String[] fixedHeaders = {"STT", "Mã SV", "Họ và tên"};
            for (String h : fixedHeaders) {
                Cell c = mainHeaderRow.createCell(col);
                c.setCellValue(h);
                c.setCellStyle(headerStyle);
                sheet.addMergedRegion(new CellRangeAddress(1, 2, col, col)); // Merge with row 2
                col++;
            }

            // For each group: add criteria columns + 1 total column
            for (int g = 0; g < criteriaGroups.length; g++) {
                int groupColCount = criteriaGroups[g].length + 1; // +1 for category total
                Cell c = mainHeaderRow.createCell(col);
                c.setCellValue(groupNames[g]);
                c.setCellStyle(headerStyle);
                if (groupColCount > 1) {
                    sheet.addMergedRegion(new CellRangeAddress(1, 1, col, col + groupColCount - 1));
                }
                col += groupColCount;
            }

            // End columns: Tổng, Tối đa, Xếp loại, Ghi chú
            String[] endHeaders = {"TỔNG", "Tối đa", "Xếp loại", "Ghi chú"};
            for (String h : endHeaders) {
                Cell c = mainHeaderRow.createCell(col);
                c.setCellValue(h);
                c.setCellStyle(h.equals("TỔNG") ? grandTotalStyle : headerStyle);
                sheet.addMergedRegion(new CellRangeAddress(1, 2, col, col));
                col++;
            }

            // Row 2: Sub headers (criteria codes + Tổng Mục)
            Row subHeaderRow = sheet.createRow(2);
            col = 3; // Skip first 3 merged columns
            
            for (int g = 0; g < criteriaGroups.length; g++) {
                for (String code : criteriaGroups[g]) {
                    Cell c = subHeaderRow.createCell(col++);
                    c.setCellValue(code);
                    c.setCellStyle(subHeaderStyle);
                }
                // Category total column
                Cell totalC = subHeaderRow.createCell(col++);
                totalC.setCellValue("Tổng");
                totalC.setCellStyle(categoryTotalHeaderStyle);
            }

            // Data rows
            int rowNum = 3;
            int maxTotal = 100;

            for (User member : members) {
                Row row = sheet.createRow(rowNum);
                col = 0;

                // STT
                Cell sttCell = row.createCell(col++);
                sttCell.setCellValue(rowNum - 2);
                sttCell.setCellStyle(centerDataStyle);

                // MSSV
                Cell mssvCell = row.createCell(col++);
                mssvCell.setCellValue(member.getUsername());
                mssvCell.setCellStyle(dataStyle);

                // Họ tên
                Cell nameCell = row.createCell(col++);
                nameCell.setCellValue(member.getFullName());
                nameCell.setCellStyle(dataStyle);

                // Get student training point
                Optional<StudentTrainingPoint> stpOpt = studentTrainingPointRepository.findByStudentAndSemester(member, currentSemester);
                
                Map<String, Integer> scoreMap = new HashMap<>();
                List<String> activityNotes = new ArrayList<>();
                int grandTotal = 0;

                if (stpOpt.isPresent()) {
                    List<TrainingPointDetail> details = trainingPointDetailRepository.findByStudentTrainingPoint(stpOpt.get());
                    for (TrainingPointDetail d : details) {
                        scoreMap.put(d.getCriteriaCode(), scoreMap.getOrDefault(d.getCriteriaCode(), 0) + d.getScore());
                        if (d.getDescription() != null && !d.getDescription().isEmpty()) {
                            activityNotes.add(d.getDescription());
                        }
                    }
                    grandTotal = stpOpt.get().getTotalScore() != null ? stpOpt.get().getTotalScore() : 0;
                }

                // Fill scores for each criteria + category total
                for (String[] group : criteriaGroups) {
                    int categorySum = 0;
                    for (String code : group) {
                        Cell c = row.createCell(col++);
                        Integer score = scoreMap.get(code);
                        if (score != null && score > 0) {
                            c.setCellValue(score);
                            categorySum += score;
                        }
                        c.setCellStyle(centerDataStyle);
                    }
                    // Category total
                    Cell catTotalCell = row.createCell(col++);
                    catTotalCell.setCellValue(categorySum);
                    catTotalCell.setCellStyle(categoryTotalDataStyle);
                }

                // Grand Total (yellow)
                Cell totalCell = row.createCell(col++);
                totalCell.setCellValue(grandTotal);
                totalCell.setCellStyle(grandTotalDataStyle);

                // Tối đa
                Cell maxCell = row.createCell(col++);
                maxCell.setCellValue(maxTotal);
                maxCell.setCellStyle(centerDataStyle);

                // Xếp loại
                Cell classificationCell = row.createCell(col++);
                classificationCell.setCellValue(getClassification(grandTotal));
                classificationCell.setCellStyle(centerDataStyle);

                // Ghi chú
                Cell noteCell = row.createCell(col++);
                noteCell.setCellValue(String.join("; ", activityNotes));
                noteCell.setCellStyle(dataStyle);

                rowNum++;
            }

            // Set column widths
            sheet.setColumnWidth(0, 1500);  // STT
            sheet.setColumnWidth(1, 3500);  // MSSV
            sheet.setColumnWidth(2, 6000);  // Họ tên
            for (int i = 3; i < col - 1; i++) {
                sheet.setColumnWidth(i, 2000);
            }
            sheet.setColumnWidth(col - 1, 12000); // Ghi chú

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private String getClassification(int score) {
        if (score >= 90) return "Xuất sắc";
        if (score >= 80) return "Tốt";
        if (score >= 65) return "Khá";
        if (score >= 50) return "Trung bình";
        if (score >= 35) return "Yếu";
        return "Kém";
    }
}
