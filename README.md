insert data into master table




import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;



@Entity
@Table(name = "tenant_config")
@Data // Requires Lombok, otherwise generate Getters/Setters
public class TenantConfig {

    @Id
    @Column(name = "tenant_id", length = 50)
    private String tenantId;

    @Column(name = "host_name", nullable = false)
    private String hostName;

    @Column(nullable = false)
    private String username;

    private Integer port = 3306;

    private String region;

    @Column(columnDefinition = "TINYINT(1)")
    private Boolean active = true;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    // Optional: Relationship to the mappings
    @OneToMany(mappedBy = "tenantConfig", cascade = CascadeType.ALL)
    private List<TenantConfigMapping> mappings;
}

------------------------------


import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "tenant_config_mapping")
@Data
public class TenantConfigMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cognito_app_client_id", nullable = false, unique = true)
    private String cognitoAppClientId;

    private String role;

    // Many mappings can belong to one Tenant
    @ManyToOne
    @JoinColumn(name = "tenant_id", nullable = false)
    private TenantConfig tenantConfig;
}

--------------------------


@Repository
public interface TenantConfigRepository extends JpaRepository<TenantConfig, String> { }

@Repository
public interface TenantMappingRepository extends JpaRepository<TenantConfigMapping, Long> { }
----------------------------------
@Service
public class TenantService {

    @Autowired
    private TenantConfigRepository configRepo;

    public void setupNewTenant() {
        // 1. Create the Parent
        TenantConfig clientB = new TenantConfig();
        clientB.setTenantId("client_b");
        clientB.setHostName("jdbc:mysql://localhost:3306/client_b_db");
        clientB.setUsername("root");
        clientB.setRegion("us-east-1");
        clientB.setActive(true);

        configRepo.save(clientB); // SQL: INSERT INTO tenant_config...

        // 2. Create the Mapping
        TenantConfigMapping mapping = new TenantConfigMapping();
        mapping.setCognitoAppClientId("app_client_id_456");
        mapping.setRole("admin");
        mapping.setTenantConfig(clientB); // Link to parent

        // If you set up CascadeType.ALL on the parent, 
        // you could also just add this mapping to clientB's list and save clientB.
    }
}
------------------

import lombok.Data;

@Data
public class TenantRegistrationRequest {
    // Fields for tenant_config
    private String tenantId;
    private String hostName;
    private String username;
    private Integer port;
    private String region;
    private Boolean active;

    // Fields for tenant_config_mapping
    private String cognitoAppClientId;
    private String role;
}


---------------------------------

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.transaction.Transactional;

@RestController
@RequestMapping("/api/tenants")
public class TenantController {

    @Autowired
    private TenantConfigRepository configRepo;

    @Autowired
    private TenantMappingRepository mappingRepo;

    @PostMapping("/register")
    @Transactional // Ensures both inserts succeed or both fail (Atomicity)
    public ResponseEntity<String> registerTenant(@RequestBody TenantRegistrationRequest request) {
        
        // 1. Create and Save the Tenant Config (The Parent)
        TenantConfig config = new TenantConfig();
        config.setTenantId(request.getTenantId());
        config.setHostName(request.getHostName());
        config.setUsername(request.getUsername());
        config.setPort(request.getPort() != null ? request.getPort() : 3306);
        config.setRegion(request.getRegion());
        config.setActive(request.getActive() != null ? request.getActive() : true);
        
        configRepo.save(config);

        // 2. Create and Save the Mapping (The Child)
        TenantConfigMapping mapping = new TenantConfigMapping();
        mapping.setCognitoAppClientId(request.getCognitoAppClientId());
        mapping.setRole(request.getRole());
        mapping.setTenantConfig(config); // Link the child to the parent object
        
        mappingRepo.save(mapping);

        return ResponseEntity.ok("Tenant and Mapping created successfully for: " + request.getTenantId());
    }
}


-----------------------
http://localhost:8080/api/tenants/register

{
    "tenantId": "client_b",
    "hostName": "jdbc:mysql://localhost:3306/client_b_db",
    "username": "root",
    "port": 3306,
    "region": "us-east-1",
    "active": true,
    "cognitoAppClientId": "app_client_id_456",
    "role": "admin"
}
