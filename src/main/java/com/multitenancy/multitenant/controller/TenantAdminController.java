package com.multitenancy.multitenant.controller;

import com.multitenancy.multitenant.dto.ApiResponse;
import com.multitenancy.multitenant.dto.TenantRegistrationRequest;
import com.multitenancy.multitenant.model.TenantInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Admin — Tenant Management", description = "Requires ROLE_ADMIN (Admin Cognito Pool token). Operates on the master database, NOT on any tenant schema.")
@SecurityRequirement(name = "CognitoBearerAuth")
@RestController
@RequestMapping("/api/v1/admin/tenants")
@RequiredArgsConstructor
//@PreAuthorize("hasRole('ADMIN')")
public class TenantAdminController {

   /* private final TenantAdminService tenantAdminService;*/

    @Operation(summary = "List all tenants", description = "Returns all registered tenants from the master database.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Tenant list",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                examples = @ExampleObject(value = """
                    {
                      "success": true,
                      "data": [
                        { "id": 1, "tenantId": "client_acme",    "schemaName": "tenant_acme",    "active": true },
                        { "id": 2, "tenantId": "client_globex",  "schemaName": "tenant_globex",  "active": true },
                        { "id": 3, "tenantId": "client_initech", "schemaName": "tenant_initech", "active": false }
                      ],
                      "timestamp": "2026-04-02T10:30:00"
                    }"""))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Caller is not an admin")
    })
    @GetMapping
    public ResponseEntity<ApiResponse<List<TenantInfo>>> getAllTenants() {
        return null;//ResponseEntity.ok(ApiResponse.success(tenantAdminService.getAllTenants()));
    }

    @Operation(
        summary = "Register a new tenant",
        description = """
            Creates a new tenant record in the master database and registers its MySQL schema.
            The schema must already exist on RDS before calling this endpoint.
            After registration, the first request by a user with `custom:tenantId` matching
            this tenant will automatically create a HikariCP connection pool for it.
            """
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Tenant registered",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                examples = @ExampleObject(value = """
                    {
                      "success": true,
                      "message": "Tenant registered",
                      "data": {
                        "id": 4,
                        "tenantId": "client_techcorp",
                        "schemaName": "tenant_techcorp",
                        "active": true,
                        "createdAt": "2026-04-02T10:30:00",
                        "updatedAt": "2026-04-02T10:30:00"
                      },
                      "timestamp": "2026-04-02T10:30:00"
                    }"""))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Tenant already exists or invalid input",
            content = @Content(examples = @ExampleObject(value = """
                    { "success": false, "message": "Tenant already exists: client_techcorp", "timestamp": "2026-04-02T10:30:00" }""")))
    })
    @PostMapping
    public ResponseEntity<ApiResponse<TenantInfo>> registerTenant(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "Tenant details",
                content = @Content(examples = @ExampleObject(value = """
                    { "tenantId": "client_techcorp", "schemaName": "tenant_techcorp" }""")))
            @Valid @RequestBody TenantRegistrationRequest request) {
       // TenantInfo info = tenantAdminService.registerTenant(request.getTenantId(), request.getSchemaName());
        return null;// ResponseEntity.status(HttpStatus.CREATED)           .body(ApiResponse.success("Tenant registered", info));
    }

    @Operation(
        summary = "Deactivate a tenant",
        description = "Marks the tenant as inactive and closes its HikariCP connection pool. All future requests from users of this tenant will be rejected."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Tenant deactivated",
            content = @Content(examples = @ExampleObject(value = """
                    {
                      "success": true,
                      "message": "Tenant deactivated",
                      "data": { "id": 4, "tenantId": "client_techcorp", "active": false },
                      "timestamp": "2026-04-02T10:30:00"
                    }"""))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Tenant not found")
    })
    @DeleteMapping("/{tenantId}")
    public ResponseEntity<ApiResponse<TenantInfo>> deactivateTenant(
            @Parameter(description = "The logical tenant identifier", example = "client_techcorp")
            @PathVariable String tenantId) {
       // TenantInfo info = tenantAdminService.deactivateTenant(tenantId);
        return null;// ResponseEntity.ok(ApiResponse.success("Tenant deactivated", info));
    }
}
