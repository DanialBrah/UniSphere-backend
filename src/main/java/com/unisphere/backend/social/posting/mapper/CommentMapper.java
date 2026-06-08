package com.unisphere.backend.social.posting.mapper;

import com.unisphere.backend.social.posting.dto.response.CommentResponse;
import com.unisphere.backend.social.posting.dto.response.PostAuthorResponse;
import com.unisphere.backend.social.posting.entity.Comment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CommentMapper {

    @Mapping(target = "id",              source = "comment.id")
    @Mapping(target = "postId",          source = "comment.postId")
    @Mapping(target = "parentCommentId", source = "comment.parentCommentId")
    @Mapping(target = "content",         source = "comment.content")
    @Mapping(target = "likesCount",      source = "comment.likesCount")
    @Mapping(target = "createdAt",       source = "comment.createdAt")
    @Mapping(target = "updatedAt",       source = "comment.updatedAt")
    @Mapping(target = "author",          source = "author")
    @Mapping(target = "liked",           source = "liked")
    @Mapping(target = "replyCount",      source = "replyCount")
    CommentResponse toResponse(Comment comment, PostAuthorResponse author, boolean liked, long replyCount);
}
