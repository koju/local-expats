package com.nepalius.post.domain

import com.nepalius.location.State
import com.nepalius.location.domain.Location
import com.nepalius.location.domain.Location.LocationId

case class CreatePost(
    title: String,
    message: Option[String],
    locationId: Option[LocationId],
)
