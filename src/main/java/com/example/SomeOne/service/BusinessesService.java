package com.example.SomeOne.service;

import com.example.SomeOne.domain.Businesses;
import com.example.SomeOne.dto.Businesses.response.FindBusinessesResponse;
import com.example.SomeOne.repository.BusinessesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BusinessesService {

    private final BusinessesRepository businessesRepository;

    public List<FindBusinessesResponse> findBusinesses(String keyword) {
        List<Businesses> businessesList = businessesRepository.findByKeyword(keyword);

        return businessesList.stream()
                .map(business -> new FindBusinessesResponse(business.getBusiness_id(), business.getBusiness_name(), business.getAddress(),
                        business.getBusiness_type())).collect(Collectors.toList());
    }
}