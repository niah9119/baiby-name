package com.baibyname.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "shortlist_entry")
public class ShortlistEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "shortlist_id", nullable = false)
    private Shortlist shortlist;

    @ManyToOne
    @JoinColumn(name = "given_name_id", nullable = false)
    private GivenName givenName;

    @ManyToOne
    @JoinColumn(name = "member_id", nullable = false)
    private ShortlistMember member;

    @Column(name = "added_at", nullable = false)
    private OffsetDateTime addedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Shortlist getShortlist() {
        return shortlist;
    }

    public void setShortlist(Shortlist shortlist) {
        this.shortlist = shortlist;
    }

    public GivenName getGivenName() {
        return givenName;
    }

    public void setGivenName(GivenName givenName) {
        this.givenName = givenName;
    }

    public ShortlistMember getMember() {
        return member;
    }

    public void setMember(ShortlistMember member) {
        this.member = member;
    }

    public OffsetDateTime getAddedAt() {
        return addedAt;
    }

    public void setAddedAt(OffsetDateTime addedAt) {
        this.addedAt = addedAt;
    }
}
