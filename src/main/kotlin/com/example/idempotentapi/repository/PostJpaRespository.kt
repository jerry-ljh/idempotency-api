package com.example.idempotentapi.repository

import com.example.idempotentapi.domain.Post
import org.springframework.data.jpa.repository.JpaRepository

interface PostJpaRepository : JpaRepository<Post, Long>