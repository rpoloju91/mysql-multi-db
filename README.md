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
