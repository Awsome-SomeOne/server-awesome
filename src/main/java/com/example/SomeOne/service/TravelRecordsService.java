package com.example.SomeOne.service;

import com.amazonaws.services.s3.AmazonS3;
import com.example.SomeOne.domain.*;
import com.example.SomeOne.dto.Businesses.response.BusinessReviewResponse;
import com.example.SomeOne.dto.TravelRecords.Request.CreateTravelRecordRequest;
import com.example.SomeOne.dto.TravelRecords.Response.TravelRecordResponse;
import com.example.SomeOne.exception.ImageStorageException;
import com.example.SomeOne.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TravelRecordsService {

    private final TravelRecordsRepository travelRecordsRepository;
    private final BusinessReviewsRepository businessReviewsRepository;
    private final BusinessReviewImagesRepository businessReviewImagesRepository;
    private final TravelPlansRepository travelPlansRepository;
    private final TravelPlaceRepository travelPlaceRepository;
    private final UserRepository userRepository;
    private final S3ImageUploadService s3ImageUploadService;
    private final AmazonS3 amazonS3Client;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    // 여행 기록 생성
    @Transactional
    public TravelRecordResponse create(List<MultipartFile> images, CreateTravelRecordRequest request) {
        // 여행 계획 조회
        TravelPlans plan = travelPlansRepository.findById(request.getPlanId())
                .orElseThrow(() -> new IllegalArgumentException("Travel plan not found with id: " + request.getPlanId()));

        // 사용자 조회
        Users user = plan.getUser();

        // 여행 기록 생성
        TravelRecords record = TravelRecords.builder()
                .user(user)
                .plan(plan)
                .recordTitle(request.getOneLineReview())
                .recordContent(request.getOverallReview())
                .publicPrivate(request.isPublicPrivate())
                .build();

        // 이미지 저장
        List<String> imageUrls = saveImages(images, record);

        // 여행 기록 저장
        TravelRecords savedRecord = travelRecordsRepository.save(record);

        // 비즈니스 리뷰 처리
        List<BusinessReviewResponse> businessReviewResponses = handleBusinessReviews(plan, user, savedRecord, request);

        // TravelRecordResponse 객체 생성 및 반환
        return new TravelRecordResponse(
                savedRecord.getRecordId(),
                request.getOneLineReview(),
                request.getOverallReview(),
                imageUrls.isEmpty() ? null : imageUrls.get(0),
                request.isPublicPrivate(),
                request.getPlanId(),
                user.getUsers_id(),
                businessReviewResponses
        );
    }

    // 이미지 저장 메서드
    private List<String> saveImages(List<MultipartFile> images, TravelRecords record) {
        List<String> imageUrls = new ArrayList<>();
        for (MultipartFile image : images) {
            String imageUrl = s3ImageUploadService.saveImage(image);
            imageUrls.add(imageUrl);
            RecordImages recordImage = RecordImages.builder()
                    .imageUrl(imageUrl)
                    .build();
            record.addRecordImage(recordImage);
        }
        return imageUrls;
    }

    // 비즈니스 리뷰 처리 메서드
    private List<BusinessReviewResponse> handleBusinessReviews(TravelPlans plan, Users user, TravelRecords record, CreateTravelRecordRequest request) {
        List<BusinessReviewResponse> businessReviewResponses = new ArrayList<>();
        List<TravelPlace> travelPlaces = travelPlaceRepository.findByTravelPlans(plan);

        for (TravelPlace travelPlace : travelPlaces) {
            Businesses business = travelPlace.getBusinesses();
            if (business != null) {
                Optional<BusinessReviews> existingReview = businessReviewsRepository.findByBusinessAndUser(business, user);
                BusinessReviews businessReview = existingReview.orElseGet(() -> {
                    // request가 null인 경우, 기본값으로 비즈니스 리뷰를 생성하지 않고, 이미 존재하는 리뷰를 가져옵니다.
                    return null;
                });

                if (businessReview != null) {
                    List<BusinessReviewImages> reviewImages = businessReviewImagesRepository.findByReview(businessReview);
                    BusinessReviewResponse businessReviewResponse = BusinessReviewResponse.fromEntity(businessReview, reviewImages);
                    businessReviewResponses.add(businessReviewResponse);
                }
            }
        }
        return businessReviewResponses;
    }

    // 여행 기록 수정
    @Transactional
    public TravelRecordResponse update(Long recordId, CreateTravelRecordRequest request, List<MultipartFile> newImages) {
        // 여행 기록 조회
        TravelRecords record = travelRecordsRepository.findById(recordId)
                .orElseThrow(() -> new IllegalArgumentException("Travel record not found with id: " + recordId));

        // 비즈니스 리뷰 처리
        List<BusinessReviewResponse> businessReviewResponses = handleBusinessReviews(record.getPlan(), record.getUser(), record, request);

        // 여행 기록 수정 및 이미지 저장
        if (request.getOneLineReview() != null) {
            record.setRecordTitle(request.getOneLineReview());
        }
        if (request.getOverallReview() != null) {
            record.setRecordContent(request.getOverallReview());
        }
        record.setPublicPrivate(request.isPublicPrivate());

        // 기존 이미지 제거 후 새 이미지 저장
        record.getRecordImages().clear();
        List<String> imageUrls = saveImages(newImages, record);

        travelRecordsRepository.save(record);

        // TravelRecordResponse 객체 생성 및 반환
        return new TravelRecordResponse(
                record.getRecordId(),
                record.getRecordTitle(),
                record.getRecordContent(),
                imageUrls.isEmpty() ? null : imageUrls.get(0),
                record.getPublicPrivate(),
                record.getPlan().getPlanId(),
                record.getUser().getUsers_id(),
                businessReviewResponses
        );
    }

    // 여행 기록 삭제
    @Transactional
    public void delete(Long recordId) {
        TravelRecords record = travelRecordsRepository.findById(recordId)
                .orElseThrow(() -> new IllegalArgumentException("Travel record not found with id: " + recordId));

        // 모든 이미지 삭제
        for (RecordImages image : record.getRecordImages()) {
            deleteImageFromS3(image.getImageUrl());
        }

        // 관련된 비즈니스 리뷰 삭제
        businessReviewsRepository.deleteByTravelRecord(record);

        // 여행 기록 삭제
        travelRecordsRepository.delete(record);
    }

    // 이미지 삭제 메서드
    private void deleteImageFromS3(String imageUrl) {
        try {
            String key = extractKeyFromUrl(imageUrl);
            amazonS3Client.deleteObject(bucket, key);
        } catch (Exception e) {
            throw new ImageStorageException("Failed to delete image from S3", e);
        }
    }

    // 이미지 URL에서 키 추출
    private String extractKeyFromUrl(String imageUrl) {
        return imageUrl.substring(imageUrl.indexOf(bucket) + bucket.length() + 1);
    }

    // 여행 기록 조회
    @Transactional
    public TravelRecordResponse getRecordById(Long recordId) {
        TravelRecords record = travelRecordsRepository.findById(recordId)
                .orElseThrow(() -> new IllegalArgumentException("Travel record not found with id: " + recordId));

        // 이미지 URL 목록 생성
        List<String> imageUrls = record.getRecordImages().stream()
                .map(RecordImages::getImageUrl)
                .collect(Collectors.toList());

        // 비즈니스 리뷰 처리
        List<BusinessReviewResponse> businessReviewResponses = handleBusinessReviews(record.getPlan(), record.getUser(), record, null);

        // TravelRecordResponse 객체 생성 및 반환
        return new TravelRecordResponse(
                record.getRecordId(),
                record.getRecordTitle(),
                record.getRecordContent(),
                imageUrls.isEmpty() ? null : imageUrls.get(0),
                record.getPublicPrivate(),
                record.getPlan().getPlanId(),
                record.getUser().getUsers_id(),
                businessReviewResponses
        );
    }

    // 사용자별 여행 기록 조회
    @Transactional
    public List<TravelRecordResponse> getRecordsByUser(Long userId) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + userId));

        List<TravelRecords> records = travelRecordsRepository.findByUserWithImagesOrderByRecordIdDesc(user);

        return records.stream()
                .map(record -> {
                    List<BusinessReviewResponse> businessReviewResponses = handleBusinessReviews(record.getPlan(), user, record, null);
                    return new TravelRecordResponse(
                            record.getRecordId(),
                            record.getRecordTitle(),
                            record.getRecordContent(),
                            record.getRecordImages().isEmpty() ? null : record.getRecordImages().get(0).getImageUrl(),
                            record.getPublicPrivate(),
                            record.getPlan().getPlanId(),
                            record.getUser().getUsers_id(),
                            businessReviewResponses
                    );
                })
                .collect(Collectors.toList());
    }

    // 여행 계획별 여행 기록 조회
    @Transactional
    public List<TravelRecordResponse> getRecordsByPlan(Long planId) {
        TravelPlans plan = travelPlansRepository.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("Travel plan not found with id: " + planId));

        List<TravelRecords> records = travelRecordsRepository.findByPlanWithImagesOrderByRecordIdDesc(plan);

        return records.stream()
                .map(record -> {
                    List<BusinessReviewResponse> businessReviewResponses = handleBusinessReviews(plan, record.getUser(), record, null);
                    return new TravelRecordResponse(
                            record.getRecordId(),
                            record.getRecordTitle(),
                            record.getRecordContent(),
                            record.getRecordImages().isEmpty() ? null : record.getRecordImages().get(0).getImageUrl(),
                            record.getPublicPrivate(),
                            record.getPlan().getPlanId(),
                            record.getUser().getUsers_id(),
                            businessReviewResponses
                    );
                })
                .collect(Collectors.toList());
    }

    // 사용자별 공개된 여행 기록 조회
    @Transactional
    public List<TravelRecordResponse> getRecordsByUserTrue(Long userId) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + userId));

        List<TravelRecords> records = travelRecordsRepository.findByUserAndPublicPrivateOrderByRecordIdDesc(user, true);

        return records.stream()
                .map(record -> {
                    List<BusinessReviewResponse> businessReviewResponses = handleBusinessReviews(record.getPlan(), user, record, null);
                    return new TravelRecordResponse(
                            record.getRecordId(),
                            record.getRecordTitle(),
                            record.getRecordContent(),
                            record.getRecordImages().isEmpty() ? null : record.getRecordImages().get(0).getImageUrl(),
                            record.getPublicPrivate(),
                            record.getPlan().getPlanId(),
                            record.getUser().getUsers_id(),
                            businessReviewResponses
                    );
                })
                .collect(Collectors.toList());
    }

    // 여행 계획별 공개된 여행 기록 조회
    @Transactional
    public List<TravelRecordResponse> getRecordsByPlanTrue(Long planId) {
        TravelPlans plan = travelPlansRepository.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("Travel plan not found with id: " + planId));

        List<TravelRecords> records = travelRecordsRepository.findByPlanAndPublicPrivateOrderByRecordIdDesc(plan, true);

        return records.stream()
                .map(record -> {
                    List<BusinessReviewResponse> businessReviewResponses = handleBusinessReviews(plan, record.getUser(), record, null);
                    return new TravelRecordResponse(
                            record.getRecordId(),
                            record.getRecordTitle(),
                            record.getRecordContent(),
                            record.getRecordImages().isEmpty() ? null : record.getRecordImages().get(0).getImageUrl(),
                            record.getPublicPrivate(),
                            record.getPlan().getPlanId(),
                            record.getUser().getUsers_id(),
                            businessReviewResponses
                    );
                })
                .collect(Collectors.toList());
    }
}
