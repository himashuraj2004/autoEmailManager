package com.epam.doable1.controller;

import com.epam.doable1.model.CourseEnrollment;
import com.epam.doable1.model.Employee;
import com.epam.doable1.model.SmtpConfig;
import com.epam.doable1.service.CourseCompletionService;
import com.epam.doable1.service.CourseCompletionService.IncompleteRecord;
import com.epam.doable1.service.DynamicMailService;
import com.epam.doable1.service.ExcelParserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/course")
public class CourseNotificationController {

    private final ExcelParserService excelParserService;
    private final CourseCompletionService courseCompletionService;
    private final DynamicMailService dynamicMailService;

    public CourseNotificationController(ExcelParserService excelParserService,
                                        CourseCompletionService courseCompletionService,
                                        DynamicMailService dynamicMailService) {
        this.excelParserService    = excelParserService;
        this.courseCompletionService = courseCompletionService;
        this.dynamicMailService    = dynamicMailService;
    }

    /**
     * POST /api/course/notify
     *
     * Multipart params:
     *   Files  : activeEmployees, courseEnrollments
     *   Config : smtpHost, smtpPort, smtpUsername, smtpPassword, fromEmail,
     *            ceoName, ceoEmail
     */
    @PostMapping("/notify")
    public ResponseEntity<Map<String, Object>> notify(
            @RequestParam("activeEmployees")   MultipartFile activeEmployeesFile,
            @RequestParam("courseEnrollments") MultipartFile courseEnrollmentsFile,
            @RequestParam("smtpHost")      String smtpHost,
            @RequestParam("smtpPort")      int    smtpPort,
            @RequestParam("smtpUsername")  String smtpUsername,
            @RequestParam("smtpPassword")  String smtpPassword,
            @RequestParam("fromEmail")     String fromEmail,
            @RequestParam("ceoName")       String ceoName,
            @RequestParam("ceoEmail")      String ceoEmail) {

        Map<String, Object> result = new LinkedHashMap<>();

        try {
            // Parse sheets
            List<Employee> activeEmployees = excelParserService.parseActiveEmployees(activeEmployeesFile);
            List<CourseEnrollment> enrollments = excelParserService.parseCourseEnrollments(courseEnrollmentsFile);

            // Intersection
            List<IncompleteRecord> incompleteRecords =
                    courseCompletionService.getIncompleteActiveEmployees(activeEmployees, enrollments);

            result.put("activeEmployeeCount", activeEmployees.size());
            result.put("courseRowCount",      enrollments.size());
            result.put("incompleteCount",     incompleteRecords.size());

            if (incompleteRecords.isEmpty()) {
                result.put("status",  "OK");
                result.put("message", "All active enrolled employees have completed their courses. No emails sent.");
                return ResponseEntity.ok(result);
            }

            // Build preview rows for UI (first 50)
            List<Map<String, String>> preview = incompleteRecords.stream()
                    .limit(50)
                    .map(r -> {
                        Map<String, String> row = new LinkedHashMap<>();
                        row.put("employeeId",  r.employee().getEmployeeId());
                        row.put("name",        r.employee().getName());
                        row.put("email",       r.employee().getEmail());
                        row.put("department",  r.employee().getDepartment());
                        row.put("courseName",  r.courseName());
                        row.put("courseId",    r.courseId());
                        row.put("mentorEmail", r.mentorEmail());
                        return row;
                    })
                    .toList();
            result.put("preview", preview);

            // Send emails
            SmtpConfig config = new SmtpConfig(smtpHost, smtpPort, smtpUsername, smtpPassword,
                    fromEmail, ceoName, ceoEmail);
            dynamicMailService.sendNotifications(incompleteRecords, config);

            result.put("status",  "OK");
            result.put("message", String.format(
                    "Emails sent successfully. %d employee(s) notified, mentors grouped by mentor email, CEO master report sent to %s.",
                    incompleteRecords.size(), ceoEmail));
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            result.put("status",  "ERROR");
            result.put("message", "Processing failed: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }
}

