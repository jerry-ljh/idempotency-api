package com.example.idempotentapi.domain

import javax.persistence.*

@Entity
class Post(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long = 0,
    @Column(nullable = false) val userId: Long,
    @Column(nullable = false) val contents: String,
)