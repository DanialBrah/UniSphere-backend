package com.unisphere.backend.social.posting.controller;

import com.unisphere.backend.identity.dto.LoginRequest;
import com.unisphere.backend.identity.entity.Admin;
import com.unisphere.backend.identity.entity.Student;
import com.unisphere.backend.identity.entity.University;
import com.unisphere.backend.identity.entity.UserStatus;
import com.unisphere.backend.identity.repository.AdminRepository;
import com.unisphere.backend.identity.repository.StudentRepository;
import com.unisphere.backend.identity.repository.UniversityRepository;
import com.unisphere.backend.social.follow.entity.Follow;
import com.unisphere.backend.social.follow.repository.FollowRepository;
import com.unisphere.backend.social.posting.AbstractPostingIntegrationTest;
import com.unisphere.backend.social.posting.dto.request.CreatePostRequest;
import com.unisphere.backend.social.posting.dto.request.UpdatePostRequest;
import com.unisphere.backend.social.posting.enums.PostType;
import com.unisphere.backend.social.posting.enums.PostVisibility;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.util.List;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PostControllerTest extends AbstractPostingIntegrationTest {

    private static final String BASE = "/api/v1/posts";

    @Autowired
    private FollowRepository followRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private UniversityRepository universityRepository;

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static final String ADMIN_PASSWORD = "Password123!";

    private String registerAdminAndGetToken(String email) throws Exception {
        Admin admin = new Admin();
        admin.setEmail(email);
        admin.setPassword(passwordEncoder.encode(ADMIN_PASSWORD));
        admin.setStatus(UserStatus.ACTIVE);
        admin.setVerified(true);
        admin.setFullName("Test Admin");
        adminRepository.save(admin);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, ADMIN_PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .at("/data/accessToken").asText();
    }

    private Long createUniversity(String email) {
        University u = new University();
        u.setEmail(email);
        u.setPassword("unused");
        u.setStatus(UserStatus.ACTIVE);
        u.setVerified(true);
        u.setName("Test University");
        return universityRepository.save(u).getId();
    }

    private Long getUserId(String token) throws Exception {
        return objectMapper.readTree(
                        mockMvc.perform(get("/api/v1/auth/me")
                                        .header("Authorization", "Bearer " + token))
                                .andReturn().getResponse().getContentAsString())
                .at("/data/id").asLong();
    }

    private void setStudentUniversityId(Long userId, Long universityId) {
        Student s = studentRepository.findById(userId).orElseThrow();
        s.setUniversityId(universityId);
        studentRepository.save(s);
    }

    // ── Create post ───────────────────────────────────────────────────────────

    @Test
    void createPost_validTextPost_returns201() throws Exception {
        String token = registerStudentAndGetToken("post.create@test.com", "MAT1001");

        CreatePostRequest req = new CreatePostRequest(
                "My First Post", "Hello UniSphere!", PostType.TEXT, PostVisibility.PUBLIC,
                null, null, null
        );

        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.content").value("Hello UniSphere!"))
                .andExpect(jsonPath("$.data.postType").value("TEXT"))
                .andExpect(jsonPath("$.data.visibility").value("PUBLIC"))
                .andExpect(jsonPath("$.data.liked").value(false))
                .andExpect(jsonPath("$.data.saved").value(false))
                .andExpect(jsonPath("$.data.author.role").value("STUDENT"));
    }

    @Test
    void createPost_withoutAuth_returns401() throws Exception {
        CreatePostRequest req = new CreatePostRequest(
                "No Auth", "Should fail", PostType.TEXT, PostVisibility.PUBLIC,
                null, null, null
        );

        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createPost_imageTypeWithMedia_returns201() throws Exception {
        String token = registerStudentAndGetToken("post.media@test.com", "MAT1002");

        CreatePostRequest.MediaItem mediaItem = new CreatePostRequest.MediaItem("posts/1/photo.jpg", "image/jpeg");
        CreatePostRequest req = new CreatePostRequest(
                "Photo Post", "Check out this photo!", PostType.IMAGE, PostVisibility.PUBLIC,
                null, null, java.util.List.of(mediaItem)
        );

        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.postType").value("IMAGE"))
                // PUBLIC posts are presigned too — a plain public URL only resolves on GCS
                .andExpect(jsonPath("$.data.media[0].mediaUrl").value(
                        "https://mock-storage.example.com/unisphere-posts/posts/1/test.jpg?get"));
    }

    // ── Feed ─────────────────────────────────────────────────────────────────

    @Test
    void getFeed_withPosts_returnsPaginatedResults() throws Exception {
        String token = registerStudentAndGetToken("feed.test@test.com", "MAT1003");
        createTextPost(token, "Feed post content");

        mockMvc.perform(get(BASE + "?page=0&size=10")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content[*].content", hasItem("Feed post content")))
                .andExpect(jsonPath("$.data.totalElements").value(greaterThanOrEqualTo(1)));
    }

    @Test
    void getFeed_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get(BASE))
                .andExpect(status().isUnauthorized());
    }

    // ── Get single post ───────────────────────────────────────────────────────

    @Test
    void getPost_existingId_returns200() throws Exception {
        String token = registerStudentAndGetToken("post.get@test.com", "MAT1004");
        Long postId = createTextPost(token, "Single post content");

        mockMvc.perform(get(BASE + "/{postId}", postId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(postId))
                .andExpect(jsonPath("$.data.content").value("Single post content"));
    }

    @Test
    void getPost_nonExistingId_returns404() throws Exception {
        String token = registerStudentAndGetToken("post.notfound@test.com", "MAT1005");

        mockMvc.perform(get(BASE + "/999999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("POST_NOT_FOUND"));
    }

    // ── Get posts by user ─────────────────────────────────────────────────────

    @Test
    void getPostsByUser_returns200WithUsersPosts() throws Exception {
        String token = registerStudentAndGetToken("posts.byuser@test.com", "MAT1006");
        Long postId = createTextPost(token, "User's post");

        Long userId = objectMapper.readTree(
                        mockMvc.perform(get("/api/v1/auth/me")
                                        .header("Authorization", "Bearer " + token))
                                .andReturn().getResponse().getContentAsString())
                .at("/data/id").asLong();

        mockMvc.perform(get(BASE + "/user/{userId}?page=0&size=10", userId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(postId));
    }

    // ── Update post ───────────────────────────────────────────────────────────

    @Test
    void updatePost_asOwner_returns200WithNewContent() throws Exception {
        String token = registerStudentAndGetToken("post.update@test.com", "MAT1007");
        Long postId = createTextPost(token, "Original content");

        UpdatePostRequest update = new UpdatePostRequest("Updated title", "Updated content", PostVisibility.FRIENDS, null, null);

        mockMvc.perform(put(BASE + "/{postId}", postId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("Updated content"))
                .andExpect(jsonPath("$.data.visibility").value("FRIENDS"));
    }

    @Test
    void updatePost_asNonOwner_returns403() throws Exception {
        String ownerToken  = registerStudentAndGetToken("post.owner@test.com", "MAT1008");
        String otherToken  = registerStudentAndGetToken("post.other@test.com", "MAT1009");
        Long postId = createTextPost(ownerToken, "Owner's post");

        UpdatePostRequest update = new UpdatePostRequest(null, "Hacked!", null, null, null);

        mockMvc.perform(put(BASE + "/{postId}", postId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + otherToken)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isForbidden());
    }

    @Test
    void updatePost_asNonOwner_onPrivatePost_returns404() throws Exception {
        String ownerToken = registerStudentAndGetToken("post.update.private.owner@test.com", "MAT1023");
        String otherToken = registerStudentAndGetToken("post.update.private.other@test.com", "MAT1024");
        Long postId = createPostWithVisibility(ownerToken, "Owner's private post", PostVisibility.PRIVATE);

        UpdatePostRequest update = new UpdatePostRequest(null, "Hacked!", null, null, null);

        mockMvc.perform(put(BASE + "/{postId}", postId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + otherToken)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isNotFound());
    }

    // ── Delete post ───────────────────────────────────────────────────────────

    @Test
    void deletePost_asOwner_softDeletesThenReturns404() throws Exception {
        String token = registerStudentAndGetToken("post.delete@test.com", "MAT1010");
        Long postId = createTextPost(token, "To be deleted");

        mockMvc.perform(delete(BASE + "/{postId}", postId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Soft-deleted post must no longer be visible
        mockMvc.perform(get(BASE + "/{postId}", postId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletePost_asNonOwner_returns403() throws Exception {
        String ownerToken = registerStudentAndGetToken("del.owner@test.com", "MAT1011");
        String otherToken = registerStudentAndGetToken("del.other@test.com", "MAT1012");
        Long postId = createTextPost(ownerToken, "Protected post");

        mockMvc.perform(delete(BASE + "/{postId}", postId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void deletePost_asNonOwner_onPrivatePost_returns404() throws Exception {
        String ownerToken = registerStudentAndGetToken("del.private.owner@test.com", "MAT1025");
        String otherToken = registerStudentAndGetToken("del.private.other@test.com", "MAT1026");
        Long postId = createPostWithVisibility(ownerToken, "Owner's private post", PostVisibility.PRIVATE);

        mockMvc.perform(delete(BASE + "/{postId}", postId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }

    // ── Like ─────────────────────────────────────────────────────────────────

    @Test
    void toggleLike_firstCall_returnsLiked() throws Exception {
        String token = registerStudentAndGetToken("like.first@test.com", "MAT1013");
        Long postId = createTextPost(token, "Likeable post");

        mockMvc.perform(post(BASE + "/{postId}/like", postId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.liked").value(true))
                .andExpect(jsonPath("$.data.likesCount").value(1));
    }

    @Test
    void toggleLike_secondCall_returnsUnliked() throws Exception {
        String token = registerStudentAndGetToken("like.toggle@test.com", "MAT1014");
        Long postId = createTextPost(token, "Toggle like post");

        // Like
        mockMvc.perform(post(BASE + "/{postId}/like", postId)
                .header("Authorization", "Bearer " + token));

        // Unlike
        mockMvc.perform(post(BASE + "/{postId}/like", postId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.liked").value(false))
                .andExpect(jsonPath("$.data.likesCount").value(0));
    }

    // ── Save ─────────────────────────────────────────────────────────────────

    @Test
    void toggleSave_firstCall_returnsSaved() throws Exception {
        String token = registerStudentAndGetToken("save.first@test.com", "MAT1015");
        Long postId = createTextPost(token, "Saveable post");

        mockMvc.perform(post(BASE + "/{postId}/save", postId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.saved").value(true));
    }

    @Test
    void toggleSave_secondCall_returnsUnsaved() throws Exception {
        String token = registerStudentAndGetToken("save.toggle@test.com", "MAT1016");
        Long postId = createTextPost(token, "Toggle save post");

        // Save
        mockMvc.perform(post(BASE + "/{postId}/save", postId)
                .header("Authorization", "Bearer " + token));

        // Unsave
        mockMvc.perform(post(BASE + "/{postId}/save", postId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.saved").value(false));
    }

    // ── Search ────────────────────────────────────────────────────────────────

    @Test
    void searchPosts_matchingQuery_returns200() throws Exception {
        String token = registerStudentAndGetToken("search.test@test.com", "MAT1017");
        createTextPost(token, "UniSphere campus app is amazing");

        mockMvc.perform(get(BASE + "/search?q=UniSphere")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray());
    }

    // ── Liked posts ───────────────────────────────────────────────────────────

    @Test
    void getLikedPosts_afterLike_containsPost() throws Exception {
        String token = registerStudentAndGetToken("liked.posts@test.com", "MAT1018");
        Long postId = createTextPost(token, "Post to like");

        mockMvc.perform(post(BASE + "/{postId}/like", postId)
                .header("Authorization", "Bearer " + token));

        mockMvc.perform(get(BASE + "/liked?page=0&size=10")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(postId))
                .andExpect(jsonPath("$.data.totalElements").value(greaterThanOrEqualTo(1)));
    }

    @Test
    void getLikedPosts_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get(BASE + "/liked"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getLikedPosts_afterUnlike_doesNotContainPost() throws Exception {
        String token = registerStudentAndGetToken("liked.toggle@test.com", "MAT1019");
        Long postId = createTextPost(token, "Post to like then unlike");

        // Like then unlike
        mockMvc.perform(post(BASE + "/{postId}/like", postId)
                .header("Authorization", "Bearer " + token));
        mockMvc.perform(post(BASE + "/{postId}/like", postId)
                .header("Authorization", "Bearer " + token));

        mockMvc.perform(get(BASE + "/liked?page=0&size=10")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.id == " + postId + ")]").isEmpty());
    }

    // ── Saved posts ───────────────────────────────────────────────────────────

    @Test
    void getSavedPosts_afterSave_containsPost() throws Exception {
        String token = registerStudentAndGetToken("saved.posts@test.com", "MAT1020");
        Long postId = createTextPost(token, "Post to save");

        mockMvc.perform(post(BASE + "/{postId}/save", postId)
                .header("Authorization", "Bearer " + token));

        mockMvc.perform(get(BASE + "/saved?page=0&size=10")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(postId))
                .andExpect(jsonPath("$.data.totalElements").value(greaterThanOrEqualTo(1)));
    }

    @Test
    void getSavedPosts_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get(BASE + "/saved"))
                .andExpect(status().isUnauthorized());
    }

    // ── Update post: media add/remove ─────────────────────────────────────────

    @Test
    void updatePost_addMedia_appendsToPost() throws Exception {
        String token = registerStudentAndGetToken("media.add@test.com", "MAT1021");
        Long postId = createTextPost(token, "Post without media");

        Long userId = objectMapper.readTree(
                        mockMvc.perform(get("/api/v1/auth/me")
                                        .header("Authorization", "Bearer " + token))
                                .andReturn().getResponse().getContentAsString())
                .at("/data/id").asLong();

        UpdatePostRequest.MediaItem item = new UpdatePostRequest.MediaItem(
                "posts/" + userId + "/new-photo.jpg", "image/jpeg");
        UpdatePostRequest req = new UpdatePostRequest(null, null, null,
                java.util.List.of(item), null);

        mockMvc.perform(put(BASE + "/{postId}", postId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.media").isArray())
                .andExpect(jsonPath("$.data.media[0].mediaUrl").value(
                        "https://mock-storage.example.com/unisphere-posts/posts/1/test.jpg?get"));
    }

    @Test
    void updatePost_removeMedia_shrinksMediaList() throws Exception {
        String token = registerStudentAndGetToken("media.remove@test.com", "MAT1022");

        // Create post with one media item
        Long userId = objectMapper.readTree(
                        mockMvc.perform(get("/api/v1/auth/me")
                                        .header("Authorization", "Bearer " + token))
                                .andReturn().getResponse().getContentAsString())
                .at("/data/id").asLong();

        CreatePostRequest.MediaItem mediaItem = new CreatePostRequest.MediaItem(
                "posts/" + userId + "/to-remove.jpg", "image/jpeg");
        com.unisphere.backend.social.posting.dto.request.CreatePostRequest createReq =
                new com.unisphere.backend.social.posting.dto.request.CreatePostRequest(
                        "Media Post", "Has media",
                        com.unisphere.backend.social.posting.enums.PostType.IMAGE,
                        com.unisphere.backend.social.posting.enums.PostVisibility.PUBLIC,
                        null, null, java.util.List.of(mediaItem));

        String createResponse = mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long postId  = objectMapper.readTree(createResponse).at("/data/id").asLong();
        Long mediaId = objectMapper.readTree(createResponse).at("/data/media/0/id").asLong();

        // Remove that media item
        UpdatePostRequest removeReq = new UpdatePostRequest(null, null, null,
                null, java.util.List.of(mediaId));

        mockMvc.perform(put(BASE + "/{postId}", postId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(removeReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.media").isEmpty());
    }

    // ── Visibility enforcement ─────────────────────────────────────────────────

    @Test
    void getPost_privateVisibility_nonOwnerReturns404() throws Exception {
        String ownerToken = registerStudentAndGetToken("private.owner@test.com", "MAT2001");
        String otherToken = registerStudentAndGetToken("private.other@test.com", "MAT2002");
        Long postId = createPostWithVisibility(ownerToken, "Secret post", PostVisibility.PRIVATE);

        mockMvc.perform(get(BASE + "/{postId}", postId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void getPost_privateVisibility_ownerReturns200() throws Exception {
        String token = registerStudentAndGetToken("private.self@test.com", "MAT2003");
        Long postId = createPostWithVisibility(token, "My secret post", PostVisibility.PRIVATE);

        mockMvc.perform(get(BASE + "/{postId}", postId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(postId));
    }

    @Test
    void getPost_friendsVisibility_nonFriendReturns404() throws Exception {
        String ownerToken = registerStudentAndGetToken("friends.owner@test.com", "MAT2004");
        String otherToken = registerStudentAndGetToken("friends.other@test.com", "MAT2005");
        Long postId = createPostWithVisibility(ownerToken, "Friends-only post", PostVisibility.FRIENDS);

        mockMvc.perform(get(BASE + "/{postId}", postId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void getPost_friendsVisibility_mutualFollowerReturns200() throws Exception {
        String ownerToken  = registerStudentAndGetToken("mutual.owner@test.com", "MAT2006");
        String friendToken = registerStudentAndGetToken("mutual.friend@test.com", "MAT2007");
        Long ownerId  = getUserId(ownerToken);
        Long friendId = getUserId(friendToken);
        Long postId = createPostWithVisibility(ownerToken, "Friends-only post", PostVisibility.FRIENDS);

        followRepository.save(new Follow(ownerId, friendId));
        followRepository.save(new Follow(friendId, ownerId));

        mockMvc.perform(get(BASE + "/{postId}", postId)
                        .header("Authorization", "Bearer " + friendToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(postId));
    }

    @Test
    void getPost_friendsVisibility_oneSidedFollowReturns404() throws Exception {
        String ownerToken    = registerStudentAndGetToken("onesided.owner@test.com", "MAT2008");
        String followerToken = registerStudentAndGetToken("onesided.follower@test.com", "MAT2009");
        Long ownerId    = getUserId(ownerToken);
        Long followerId = getUserId(followerToken);
        Long postId = createPostWithVisibility(ownerToken, "Friends-only post", PostVisibility.FRIENDS);

        // Only one direction — not mutual
        followRepository.save(new Follow(followerId, ownerId));

        mockMvc.perform(get(BASE + "/{postId}", postId)
                        .header("Authorization", "Bearer " + followerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void getPost_universityVisibility_sameUniversityReturns200() throws Exception {
        String ownerToken  = registerStudentAndGetToken("uni.owner@test.com", "MAT2010");
        String sameUniToken = registerStudentAndGetToken("uni.same@test.com", "MAT2011");
        Long ownerId   = getUserId(ownerToken);
        Long sameUniId = getUserId(sameUniToken);
        Long universityId = createUniversity("uni.school1@test.com");
        setStudentUniversityId(ownerId, universityId);
        setStudentUniversityId(sameUniId, universityId);

        Long postId = createPostWithVisibility(ownerToken, "University-only post", PostVisibility.UNIVERSITY);

        mockMvc.perform(get(BASE + "/{postId}", postId)
                        .header("Authorization", "Bearer " + sameUniToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(postId));
    }

    @Test
    void getPost_universityVisibility_differentUniversityReturns404() throws Exception {
        String ownerToken = registerStudentAndGetToken("uni2.owner@test.com", "MAT2012");
        String otherToken = registerStudentAndGetToken("uni2.other@test.com", "MAT2013");
        Long ownerId = getUserId(ownerToken);
        setStudentUniversityId(ownerId, createUniversity("uni.school2@test.com"));
        // otherToken's student is left with no universityId — different (absent) affiliation

        Long postId = createPostWithVisibility(ownerToken, "University-only post", PostVisibility.UNIVERSITY);

        mockMvc.perform(get(BASE + "/{postId}", postId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void getPostsByUser_excludesPrivatePostForStranger_includesForOwner() throws Exception {
        String ownerToken = registerStudentAndGetToken("byuser.private.owner@test.com", "MAT2014");
        String otherToken = registerStudentAndGetToken("byuser.private.other@test.com", "MAT2015");
        Long ownerId = getUserId(ownerToken);
        Long privatePostId = createPostWithVisibility(ownerToken, "Owner's private post", PostVisibility.PRIVATE);

        mockMvc.perform(get(BASE + "/user/{userId}?page=0&size=10", ownerId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.id == " + privatePostId + ")]").isEmpty());

        mockMvc.perform(get(BASE + "/user/{userId}?page=0&size=10", ownerId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.id == " + privatePostId + ")]").exists());
    }

    @Test
    void toggleLike_onPrivatePost_asNonOwner_returns404() throws Exception {
        String ownerToken = registerStudentAndGetToken("like.private.owner@test.com", "MAT2016");
        String otherToken = registerStudentAndGetToken("like.private.other@test.com", "MAT2017");
        Long postId = createPostWithVisibility(ownerToken, "Private post", PostVisibility.PRIVATE);

        mockMvc.perform(post(BASE + "/{postId}/like", postId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void toggleSave_onPrivatePost_asNonOwner_returns404() throws Exception {
        String ownerToken = registerStudentAndGetToken("save.private.owner@test.com", "MAT2018");
        String otherToken = registerStudentAndGetToken("save.private.other@test.com", "MAT2019");
        Long postId = createPostWithVisibility(ownerToken, "Private post", PostVisibility.PRIVATE);

        mockMvc.perform(post(BASE + "/{postId}/save", postId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void getPost_privatePostWithMedia_returnsPresignedNotPassthroughUrl() throws Exception {
        String token = registerStudentAndGetToken("private.media@test.com", "MAT2020");
        Long userId = getUserId(token);
        CreatePostRequest.MediaItem mediaItem = new CreatePostRequest.MediaItem(
                "posts/" + userId + "/private-photo.jpg", "image/jpeg");
        Long postId = createPostWithVisibilityAndMedia(
                token, "Private photo post", PostVisibility.PRIVATE, List.of(mediaItem));

        mockMvc.perform(get(BASE + "/{postId}", postId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.media[0].mediaUrl").value(
                        "https://mock-storage.example.com/unisphere-posts/posts/1/test.jpg?get"));
    }

    // ── Admin bypass ─────────────────────────────────────────────────────────

    @Test
    void getPost_asAdmin_onOthersPrivatePost_returns200() throws Exception {
        String ownerToken = registerStudentAndGetToken("admin.view.owner@test.com", "MAT4001");
        Long postId = createPostWithVisibility(ownerToken, "Private post", PostVisibility.PRIVATE);
        String adminToken = registerAdminAndGetToken("admin.view@test.com");

        mockMvc.perform(get(BASE + "/{postId}", postId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(postId));
    }

    @Test
    void deletePost_asAdmin_onOthersPrivatePost_returns200() throws Exception {
        String ownerToken = registerStudentAndGetToken("admin.delete.owner@test.com", "MAT4002");
        Long postId = createPostWithVisibility(ownerToken, "Private post", PostVisibility.PRIVATE);
        String adminToken = registerAdminAndGetToken("admin.delete@test.com");

        mockMvc.perform(delete(BASE + "/{postId}", postId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    // ── Liked/saved/search visibility filtering ─────────────────────────────

    @Test
    void getLikedPosts_afterPostBecomesPrivate_excludedFromList() throws Exception {
        String ownerToken = registerStudentAndGetToken("liked.vis.owner@test.com", "MAT4003");
        String otherToken = registerStudentAndGetToken("liked.vis.other@test.com", "MAT4004");
        Long postId = createTextPost(ownerToken, "Post to like then hide"); // PUBLIC by default

        mockMvc.perform(post(BASE + "/{postId}/like", postId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk());

        UpdatePostRequest makePrivate = new UpdatePostRequest(null, null, PostVisibility.PRIVATE, null, null);
        mockMvc.perform(put(BASE + "/{postId}", postId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + ownerToken)
                        .content(objectMapper.writeValueAsString(makePrivate)))
                .andExpect(status().isOk());

        mockMvc.perform(get(BASE + "/liked?page=0&size=10")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.id == " + postId + ")]").isEmpty());
    }

    @Test
    void getSavedPosts_afterPostBecomesPrivate_excludedFromList() throws Exception {
        String ownerToken = registerStudentAndGetToken("saved.vis.owner@test.com", "MAT4005");
        String otherToken = registerStudentAndGetToken("saved.vis.other@test.com", "MAT4006");
        Long postId = createTextPost(ownerToken, "Post to save then hide");

        mockMvc.perform(post(BASE + "/{postId}/save", postId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk());

        UpdatePostRequest makePrivate = new UpdatePostRequest(null, null, PostVisibility.PRIVATE, null, null);
        mockMvc.perform(put(BASE + "/{postId}", postId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + ownerToken)
                        .content(objectMapper.writeValueAsString(makePrivate)))
                .andExpect(status().isOk());

        mockMvc.perform(get(BASE + "/saved?page=0&size=10")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.id == " + postId + ")]").isEmpty());
    }

    @Test
    void searchPosts_afterPostBecomesPrivate_excludedFromResults() throws Exception {
        String ownerToken = registerStudentAndGetToken("search.vis.owner@test.com", "MAT4007");
        String otherToken = registerStudentAndGetToken("search.vis.other@test.com", "MAT4008");
        Long postId = createTextPost(ownerToken, "UnisphereSearchVisibilityMarker content");

        mockMvc.perform(get(BASE + "/search?q=UnisphereSearchVisibilityMarker")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.id == " + postId + ")]").exists());

        UpdatePostRequest makePrivate = new UpdatePostRequest(null, null, PostVisibility.PRIVATE, null, null);
        mockMvc.perform(put(BASE + "/{postId}", postId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + ownerToken)
                        .content(objectMapper.writeValueAsString(makePrivate)))
                .andExpect(status().isOk());

        mockMvc.perform(get(BASE + "/search?q=UnisphereSearchVisibilityMarker")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.id == " + postId + ")]").isEmpty());
    }

    // ── Presign fail-open ────────────────────────────────────────────────────

    @Test
    void getPost_presignedGetFails_returnsNullMediaUrlInsteadOf500() throws Exception {
        String token = registerStudentAndGetToken("presign.fail@test.com", "MAT4009");
        Long userId = getUserId(token);
        CreatePostRequest.MediaItem mediaItem = new CreatePostRequest.MediaItem(
                "posts/" + userId + "/fail-photo.jpg", "image/jpeg");
        Long postId = createPostWithVisibilityAndMedia(
                token, "Private photo post", PostVisibility.PRIVATE, List.of(mediaItem));

        Mockito.when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
                .thenThrow(new RuntimeException("Simulated signer failure"));

        // Now that only the bare key is persisted there is no URL left to fall back to, so the
        // field comes back null rather than a fabricated public URL that would 403 on Garage/B2.
        // The request itself must still succeed — one bad key should not fail the whole response.
        mockMvc.perform(get(BASE + "/{postId}", postId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.media[0].mediaUrl").doesNotExist());
    }
}
