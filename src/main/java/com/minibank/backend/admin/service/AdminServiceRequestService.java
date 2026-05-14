package com.minibank.backend.admin.service;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minibank.backend.account.entity.Account;
import com.minibank.backend.account.repository.AccountRepository;
import com.minibank.backend.admin.dto.AdminServiceRequestDetail;
import com.minibank.backend.admin.dto.AdminServiceRequestSummary;
import com.minibank.backend.admin.entity.AdminUser;
import com.minibank.backend.admin.repository.AdminUserRepository;
import com.minibank.backend.support.dto.LimitChangeRequestResponse;
import com.minibank.backend.support.entity.LimitChangeRequest;
import com.minibank.backend.support.entity.ServiceRequest;
import com.minibank.backend.support.repository.LimitChangeRequestRepository;
import com.minibank.backend.support.repository.ServiceRequestRepository;
import com.minibank.backend.user.entity.User;
import com.minibank.backend.user.repository.UserRepository;

@Service
public class AdminServiceRequestService {
	private final ServiceRequestRepository serviceRequestRepository;
	private final LimitChangeRequestRepository limitChangeRequestRepository;
	private final UserRepository userRepository;
	private final AccountRepository accountRepository;
	private final AdminUserRepository adminUserRepository;
	private final ObjectMapper objectMapper;

	public AdminServiceRequestService(
		ServiceRequestRepository serviceRequestRepository,
		LimitChangeRequestRepository limitChangeRequestRepository,
		UserRepository userRepository,
		AccountRepository accountRepository,
		AdminUserRepository adminUserRepository,
		ObjectMapper objectMapper
	) {
		this.serviceRequestRepository = serviceRequestRepository;
		this.limitChangeRequestRepository = limitChangeRequestRepository;
		this.userRepository = userRepository;
		this.accountRepository = accountRepository;
		this.adminUserRepository = adminUserRepository;
		this.objectMapper = objectMapper;
	}

	@Transactional(readOnly = true)
	public List<AdminServiceRequestSummary> list(String status, String type) {
		String statusFilter = normalize(status);
		String typeFilter = normalize(type);
		return serviceRequestRepository.findAll().stream()
			.filter(req -> statusFilter == null || statusFilter.equalsIgnoreCase(req.getStatus()))
			.filter(req -> typeFilter == null || typeFilter.equalsIgnoreCase(req.getRequestType()))
			.map(this::toSummary)
			.toList();
	}

	@Transactional(readOnly = true)
	public AdminServiceRequestDetail getDetail(long requestId) {
		ServiceRequest request = serviceRequestRepository.findById(requestId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Service request not found"));
		LimitChangeRequestResponse limitChange = limitChangeRequestRepository.findByServiceRequestId(request.getId())
			.map(this::toLimitChangeResponse)
			.orElse(null);
		User user = request.getUser();
		return new AdminServiceRequestDetail(
			request.getId(),
			request.getRequestType(),
			request.getTitle(),
			request.getDescription(),
			request.getPayloadJson(),
			request.getStatus(),
			request.getPriorityTag(),
			request.getSubmittedAt(),
			request.getProcessedAt(),
			request.getProcessNote(),
			user == null ? null : user.getId(),
			user == null ? null : user.getFullName(),
			user == null ? null : user.getPhone(),
			limitChange
		);
	}

	@Transactional
	public void approve(long requestId, long adminUserId, String note) {
		ServiceRequest request = loadPending(requestId);
		AdminUser adminUser = adminUserRepository.findById(adminUserId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Admin user not found"));

		String type = normalize(request.getRequestType());
		if ("limit_change".equals(type)) {
			LimitChangeRequest limitChange = limitChangeRequestRepository.findByServiceRequestId(request.getId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Limit change request not found"));
			Account account = accountRepository.findById(limitChange.getAccount().getId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
			account.setDailyTransferLimit(limitChange.getRequestedDailyTransferLimit());
			accountRepository.save(account);
		} else if ("profile_change".equals(type)) {
			applyProfilePayload(request);
		}

		markProcessed(request, adminUser, "APPROVED", note);
	}

	@Transactional
	public void reject(long requestId, long adminUserId, String note) {
		ServiceRequest request = loadPending(requestId);
		AdminUser adminUser = adminUserRepository.findById(adminUserId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Admin user not found"));
		markProcessed(request, adminUser, "REJECTED", note);
	}

	private ServiceRequest loadPending(long requestId) {
		ServiceRequest request = serviceRequestRepository.findById(requestId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Service request not found"));
		if (!"submitted".equalsIgnoreCase(request.getStatus())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Service request is not pending");
		}
		return request;
	}

	private void markProcessed(ServiceRequest request, AdminUser adminUser, String status, String note) {
		request.setStatus(status);
		request.setAssignedTo(adminUser);
		request.setProcessedAt(Instant.now());
		request.setProcessNote(note);
		serviceRequestRepository.save(request);
	}

	private void applyProfilePayload(ServiceRequest request) {
		String payloadJson = request.getPayloadJson();
		if (payloadJson == null || payloadJson.isBlank()) {
			return;
		}
		User user = userRepository.findById(request.getUser().getId())
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
		try {
			Map<String, Object> payload = objectMapper.readValue(payloadJson, new TypeReference<>() {});
			Object fullName = payload.get("fullName");
			Object dob = payload.get("dob");
			Object address = payload.get("address");
			if (fullName instanceof String name && !name.isBlank()) {
				user.setFullName(name.trim());
			}
			if (dob instanceof String dobValue && !dobValue.isBlank()) {
				user.setDob(java.time.LocalDate.parse(dobValue));
			}
			if (address instanceof String addressValue && !addressValue.isBlank()) {
				user.setAddress(addressValue.trim());
			}
			userRepository.save(user);
		} catch (JsonProcessingException | DateTimeParseException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid profile payload");
		}
	}

	private AdminServiceRequestSummary toSummary(ServiceRequest request) {
		User user = request.getUser();
		return new AdminServiceRequestSummary(
			request.getId(),
			request.getRequestType(),
			request.getTitle(),
			request.getStatus(),
			request.getPriorityTag(),
			request.getSubmittedAt(),
			user == null ? null : user.getId(),
			user == null ? null : user.getFullName(),
			user == null ? null : user.getPhone()
		);
	}

	private LimitChangeRequestResponse toLimitChangeResponse(LimitChangeRequest request) {
		Account account = request.getAccount();
		ServiceRequest serviceRequest = request.getServiceRequest();
		return new LimitChangeRequestResponse(
			request.getId(),
			serviceRequest.getId(),
			account.getId(),
			account.getAccountNumber(),
			account.getAccountName(),
			request.getCurrentDailyTransferLimit(),
			request.getRequestedDailyTransferLimit(),
			request.getReason(),
			serviceRequest.getStatus(),
			serviceRequest.getSubmittedAt(),
			serviceRequest.getProcessedAt(),
			serviceRequest.getProcessNote()
		);
	}

	private static String normalize(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed.toLowerCase();
	}
}
