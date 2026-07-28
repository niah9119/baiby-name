package com.baibyname.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "name_style")
public class NameStyle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "given_name_id", nullable = false)
    private GivenName givenName;

    @Column(name = "style_score")
    private Short styleScore;  // -100 (traditional) to +100 (modern), nullable

    @Column(name = "syllable_count")
    private Short syllableCount;  // nullable

    @Column(name = "sound_character")
    private Short soundCharacter;  // -100 (soft) to +100 (strong), nullable

    @Column(name = "origin")
    private String origin;  // nullable

    @Column(name = "international")
    private Boolean international;  // works across many languages

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public GivenName getGivenName() {
        return givenName;
    }

    public void setGivenName(GivenName givenName) {
        this.givenName = givenName;
    }

    public Short getStyleScore() {
        return styleScore;
    }

    public void setStyleScore(Short styleScore) {
        this.styleScore = styleScore;
    }

    public Short getSyllableCount() {
        return syllableCount;
    }

    public void setSyllableCount(Short syllableCount) {
        this.syllableCount = syllableCount;
    }

    public Short getSoundCharacter() {
        return soundCharacter;
    }

    public void setSoundCharacter(Short soundCharacter) {
        this.soundCharacter = soundCharacter;
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public Boolean getInternational() {
        return international;
    }

    public void setInternational(Boolean international) {
        this.international = international;
    }
}
