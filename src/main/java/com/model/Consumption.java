package com.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "consumption")
public class Consumption {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private double consumptionKwh;

    @Column(nullable = false)
    private double productionKwh;

      @ManyToOne
    @JoinColumn(name = "user_id")  // Foreign key in Consumption table
    private User user;

    // Getters and Setters
    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }

    public LocalDate getDate() {
        return date;
    }
    public void setDate(LocalDate date) {
        this.date = date;
    }

    public double getConsumptionKwh() {
        return consumptionKwh;
    }
    public void setConsumptionKwh(double consumptionKwh) {
        this.consumptionKwh = consumptionKwh;
    }
    public double getProductionKwh() {
    return productionKwh;
    }
      public User getUser() {
        return user;
    }
    public void setUser(User user) {
        this.user = user;
    }

    public void setProductionKwh(double productionKwh) {
        this.productionKwh = productionKwh;
    }

    }