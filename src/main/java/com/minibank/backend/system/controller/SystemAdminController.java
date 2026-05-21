package com.minibank.backend.system.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.minibank.backend.system.entity.SystemLog;
import com.minibank.backend.system.service.SystemLogService;

@RestController
@RequestMapping("/api/admin")
public class SystemAdminController {

    private final SystemLogService systemLogService;

    public SystemAdminController(SystemLogService systemLogService) {
        this.systemLogService = systemLogService;
    }

    @GetMapping("/audit-logs")
    public List<SystemLog> getAuditLogs() {
        return systemLogService.getAllLogs();
    }

    @GetMapping("/roles")
    public List<Map<String, Object>> getRoles() {

        return List.of(
                Map.of(
                        "name", "Quản trị viên",
                        "code", "ADMIN",
                        "description", "Toàn quyền hệ thống",
                        "totalUsers", 3,
                        "color", "red"),
                Map.of(
                        "name", "Quản lý",
                        "code", "MANAGER",
                        "description", "Duyệt nghiệp vụ",
                        "totalUsers", 8,
                        "color", "orange"),
                Map.of(
                        "name", "Nhân viên",
                        "code", "STAFF",
                        "description", "Xử lý hồ sơ",
                        "totalUsers", 25,
                        "color", "blue"));
    }
}