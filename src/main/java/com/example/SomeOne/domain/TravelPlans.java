package com.example.SomeOne.domain;

import com.example.SomeOne.domain.enums.TravelStatus;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor
public class TravelPlans { //여행기록

    @Id @GeneratedValue
    private Long planId;

    @OneToMany(mappedBy = "travelPlans", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TravelPlace> travelPlaces = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinColumn(name = "users_id")
    private Users user;
    private String plan_name;
    private LocalDate startDate;
    private LocalDate endDate;
    private TravelStatus status;

    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinColumn(name = "island_id")
    private Island island;

    @Builder
    public TravelPlans(Users user, String plan_name, LocalDate startDate, LocalDate endDate, Island island) {
        this.user = user;
        this.plan_name = plan_name;
        this.startDate = startDate;
        this.endDate = endDate;
        this.island = island;
        this.status = TravelStatus.여행전;
    }

    public void startTravel() {
        this.status = TravelStatus.여행중;
    }

    public void finishTravel() {
        this.status = TravelStatus.여행완료;
    }
}
