package com.baibyname.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "name_famous_bearer")
public class NameFamousBearer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "given_name_id", nullable = false)
    private GivenName givenName;

    @ManyToOne
    @JoinColumn(name = "famous_bearer_id", nullable = false)
    private FamousBearer famousBearer;

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

    public FamousBearer getFamousBearer() {
        return famousBearer;
    }

    public void setFamousBearer(FamousBearer famousBearer) {
        this.famousBearer = famousBearer;
    }
}
