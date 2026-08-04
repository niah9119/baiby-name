package com.baibyname.domain;

import com.baibyname.dto.CountryStat;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "given_name")
public class GivenName {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @OneToMany(mappedBy = "givenName")
    private Set<NameStat> nameStats = new HashSet<>();

    @OneToMany(mappedBy = "givenName")
    private Set<NameFamousBearer> famousBearers = new HashSet<>();

    @OneToOne(mappedBy = "givenName", cascade = CascadeType.ALL, orphanRemoval = true)
    private NameStyle nameStyle;

    @OneToMany(mappedBy = "givenName")
    private Set<ShortlistEntry> shortlistEntries = new HashSet<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Set<NameStat> getNameStats() {
        return nameStats;
    }

    public void setNameStats(Set<NameStat> nameStats) {
        this.nameStats = nameStats;
    }

    public Set<NameFamousBearer> getFamousBearers() {
        return famousBearers;
    }

    public void setFamousBearers(Set<NameFamousBearer> famousBearers) {
        this.famousBearers = famousBearers;
    }

    public NameStyle getNameStyle() {
        return nameStyle;
    }

    public void setNameStyle(NameStyle nameStyle) {
        this.nameStyle = nameStyle;
    }

    public Set<ShortlistEntry> getShortlistEntries() {
        return shortlistEntries;
    }

    public void setShortlistEntries(Set<ShortlistEntry> shortlistEntries) {
        this.shortlistEntries = shortlistEntries;
    }

    /**
     * Get aggregated statistics per country.
     * Returns one CountryStat for each country where this name appears,
     * with aggregated data (year range, best rank, years in top 100).
     */
    public List<CountryStat> getCountryStats() {
        if (nameStats == null || nameStats.isEmpty()) {
            return List.of();
        }

        // Group stats by country code
        var statsByCountry = nameStats.stream()
                .collect(Collectors.groupingBy(
                        ns -> ns.getCountry().getCode(),
                        Collectors.mapping(ns -> ns, Collectors.toList())
                ));

        // Convert each group to CountryStat
        return statsByCountry.entrySet().stream()
                .map(entry -> CountryStat.from(entry.getKey(), entry.getValue()))
                .toList();
    }
}
