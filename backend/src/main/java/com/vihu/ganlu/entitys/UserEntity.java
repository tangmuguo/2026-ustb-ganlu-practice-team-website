package com.vihu.ganlu.entitys;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Date;

@Data
public class UserEntity {
    Integer id;
    String username;
    String password;
    String imageUrl;
    String teamname;
    String helplocation;
    String helpschool;
    String realname;
    String belongschool;
    String grade;
    String phone;
    Integer level;
    String displayName;
    String verificationStatus;
    String verificationMethod;
    Date verifiedAt;
    Integer verifiedByUserId;
    String guardianConsentStatus;
    String guardianConsentVersion;
    Date guardianConsentedAt;
    String privacyConsentVersion;
    Date privacyConsentedAt;
    Integer sessionVersion;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    String imageUploadToken;
    @JsonIgnore
    Integer imageUploadUserId;
}
