package com.vibecircle.interest;

import jakarta.persistence.*;

@Entity
@Table(name = "interests")
public class Interest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private String name;

    protected Interest() {}

    public Long getId() { return id; }
    public String getCategory() { return category; }
    public String getName() { return name; }

    @Override
    public boolean equals(Object o) {
        return o instanceof Interest other && id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id == null ? 0 : id.hashCode();
    }
}
