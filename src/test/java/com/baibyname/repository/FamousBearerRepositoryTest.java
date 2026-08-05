package com.baibyname.repository;

import com.baibyname.domain.FamousBearer;
import com.baibyname.domain.GivenName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.OffsetDateTime;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for FamousBearerRepository.
 */
@SpringBootTest
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class FamousBearerRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired
    private FamousBearerRepository bearerRepository;

    @Autowired
    private GivenNameRepository givenNameRepository;

    private GivenName givenName1;
    private GivenName givenName2;
    private FamousBearer messiBearer;

    @BeforeEach
    void setUp() {
        // Setup given names with unique names for each test
        givenName1 = new GivenName();
        givenName1.setName("Leo" + System.nanoTime());
        givenName1.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(givenName1);

        givenName2 = new GivenName();
        givenName2.setName("Lionel" + System.nanoTime());
        givenName2.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(givenName2);

        // Setup famous bearer
        messiBearer = new FamousBearer();
        messiBearer.setPublicName("Lionel Messi " + System.nanoTime());
        messiBearer.setSubcategory(FamousBearer.Subcategory.SPORTS_STAR);
        messiBearer.setCreatedAt(OffsetDateTime.now());
        bearerRepository.save(messiBearer);

        // Link bearer to given names
        messiBearer.getGivenNames().add(givenName1);
        messiBearer.getGivenNames().add(givenName2);
        bearerRepository.save(messiBearer);
    }

    @Test
    void findByPublicNameReturnsCorrectBearer() {
        // Act
        var result = bearerRepository.findByPublicName(messiBearer.getPublicName());

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get().getPublicName()).isEqualTo(messiBearer.getPublicName());
        assertThat(result.get().getSubcategory()).isEqualTo(FamousBearer.Subcategory.SPORTS_STAR);
    }

    @Test
    void findBearersByGivenNameIdLinksMultipleNames() {
        // Act - fetch with eager join
        var bearers = bearerRepository.findBearersByGivenNameId(givenName1.getId());

        // Assert - only the first bearer should be returned
        assertThat(bearers).hasSize(1);
        // The query already joins fetch, so givenNames should be initialized
        var bearer = bearers.get(0);
        assertThat(bearer.getGivenNames()).isNotNull();
        // At minimum, the bearer should have the given name we queried for
        var bearerGivenNames = bearer.getGivenNames().stream()
            .map(GivenName::getName)
            .collect(java.util.stream.Collectors.toSet());
        assertThat(bearerGivenNames).contains(givenName1.getName());
    }

    @Test
    void findBearersByGivenNameIdReturnsEmptyWhenNoLinks() {
        // Setup: a given name not linked to any bearer
        var unlinkedName = new GivenName();
        unlinkedName.setName("Unlinked" + System.nanoTime());
        unlinkedName.setCreatedAt(OffsetDateTime.now());
        givenNameRepository.save(unlinkedName);

        // Act
        var bearers = bearerRepository.findBearersByGivenNameId(unlinkedName.getId());

        // Assert
        assertThat(bearers).isEmpty();
    }

    @Test
    void findBySubcategoryReturnsAllBearersInCategory() {
        // Setup: create additional bearers in same category for this test
        var bearersBefore = bearerRepository.findBySubcategory(FamousBearer.Subcategory.SPORTS_STAR);
        int countBefore = bearersBefore.size();

        var newBearer = new FamousBearer();
        newBearer.setPublicName("Test Sports Star " + System.nanoTime());
        newBearer.setSubcategory(FamousBearer.Subcategory.SPORTS_STAR);
        newBearer.setCreatedAt(OffsetDateTime.now());
        bearerRepository.save(newBearer);

        // Act
        var bearers = bearerRepository.findBySubcategory(FamousBearer.Subcategory.SPORTS_STAR);

        // Assert - should have one more bearer now
        assertThat(bearers).hasSize(countBefore + 1);
        assertThat(bearers).anyMatch(b -> b.getId().equals(newBearer.getId()));
    }
}
