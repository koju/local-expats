package com.nepalius.post

object PostMapper:

  def toCreatePost(dto: CreatePostDto) =
    CreatePost(dto.title, dto.message, dto.locationId, dto.image)

  def toCreateComment(dto: CreateCommentDto) =
    CreateComment(dto.message, dto.image)

  def toPostWithCommentsDto(postWithComments: PostWithComments) =
    PostWithCommentsDto(
      toPostDto(postWithComments.post),
      postWithComments.comments.map(toCommentDto),
    )

  def toPostDto(post: Post): PostDto =
    toPostDto(PostView.fromPost(post, 0))

  def toPostDto(post: PostView) =
    PostDto(
      post.id,
      post.title,
      post.message,
      post.locationId,
      post.createdAt,
      post.image,
      post.noOfComments,
    )

  private def toCommentDto(comment: Comment) =
    CommentDto(
      comment.id,
      comment.postId,
      comment.message,
      comment.image,
      comment.createdAt,
    )
