package com.baibyname.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "famous_bearer")
public class FamousBearer {

    public enum Subcategory {
        ROYALTY, MOVIE_STAR, SPORTS_STAR
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_name", nullable = false)
    private String publicName;

    @Enumerated(EnumType.STRING)
    @Column(name = "subcategory", nullable = false)
    private Subcategory subcategory;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @ManyToMany
    @JoinTable(
        name = "name_famous_bearer",
        joinColumns = @JoinColumn(name = "famous_bearer_id"),
        inverseJoinColumns = @JoinColumn(name = "given_name_id")
    )
    private Set<GivenName> givenNames = new HashSet<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPublicName() {
        return publicName;
    }

    public void setPublicName(String publicName) {
        this.publicName = publicName;
    }

    public Subcategory getSubcategory() {
        return subcategory;
    }

    public void setSubcategory(Subcategory subcategory) {
        this.subcategory = subcategory;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Set<GivenName> getGivenNames() {
        return givenNames;
    }

    public void setGivenNames(Set<GivenName> givenNames) {
        this.givenNames = givenNames;
    }
}
