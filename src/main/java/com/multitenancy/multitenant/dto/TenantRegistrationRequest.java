package com.multitenancy.multitenant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Schema(description = "Request to register a new tenant")
@Data
public class TenantRegistrationRequest {

    @Schema(
        description = "Unique logical tenant identifier. Must match the custom:tenantId attribute set on Cognito users.",
        example = "client_techcorp",
        pattern = "^[a-zA-Z0-9_-]{2,50}$"
    )
    @NotBlank(message = "Tenant ID is required")
    @Pattern(regexp = "^[a-zA-Z0-9_-]{2,50}$",
             message = "Tenant ID must be 2-50 alphanumeric/underscore/hyphen characters")
    private String tenantId;

    @Schema(
        description = "MySQL schema/database name for this tenant on RDS. Schema must already exist.",
        example = "tenant_techcorp",
        pattern = "^[a-zA-Z0-9_]{2,100}$"
    )
    @NotBlank(message = "Schema name is required")
    @Pattern(regexp = "^[a-zA-Z0-9_]{2,100}$",
             message = "Schema name must be 2-100 alphanumeric/underscore characters")
    private String schemaName;
}
