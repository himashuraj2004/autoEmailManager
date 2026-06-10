package com.epam.doable1.controller;

import com.epam.doable1.service.DemoSheetService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/demo")
public class DemoSheetController {

    private final DemoSheetService demoSheetService;

    public DemoSheetController(DemoSheetService demoSheetService) {
        this.demoSheetService = demoSheetService;
    }

    @GetMapping("/active-employees")
    public ResponseEntity<byte[]> downloadActiveEmployees() throws Exception {
        byte[] bytes = demoSheetService.generateActiveEmployeeSheet();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"demo_active_employees.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }

    @GetMapping("/course-enrollments")
    public ResponseEntity<byte[]> downloadCourseProgress() throws Exception {
        byte[] bytes = demoSheetService.generateCourseProgressSheet();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"demo_course_progress.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }
}
