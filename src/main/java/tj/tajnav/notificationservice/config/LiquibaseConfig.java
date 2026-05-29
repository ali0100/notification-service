package tj.tajnav.notificationservice.config;

import liquibase.integration.spring.SpringLiquibase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jpa.autoconfigure.EntityManagerFactoryDependsOnPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class LiquibaseConfig {

    @Value("${spring.liquibase.change-log:classpath:db/changelog/db.changelog-master.yaml}")
    private String changeLog;

    @Bean
    public SpringLiquibase liquibase(DataSource dataSource) {
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog(changeLog);
        return liquibase;
    }

    // Ensures EntityManagerFactory (and therefore Hibernate schema validation)
    // initialises only after Liquibase has finished running all changesets.
    @Bean
    public static EntityManagerFactoryDependsOnPostProcessor liquibaseJpaDependency() {
        return new EntityManagerFactoryDependsOnPostProcessor("liquibase");
    }
}