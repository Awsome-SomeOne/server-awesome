package com.example.SomeOne.dto.TravelPlans.response;

import com.example.SomeOne.domain.enums.Business_category;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TravelPlaceResponse {
    private Long id;
    private String placeName;
    private String address;
    private Business_category category;
    private LocalDate date;
    private Integer order;
    private String imgUrl;
}