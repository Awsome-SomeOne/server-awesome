package com.example.SomeOne.dto.Login.Response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@ToString
public class SocialUserResponse {
    private String id; //닉네임
    private String email;
    private String name;
    private String gender;
    private String birthday;
}