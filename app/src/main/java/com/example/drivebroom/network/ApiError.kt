package com.example.drivebroom.network

data class ApiError(
    val error: String?,
    val message: String?
)

class PendingApprovalException : Exception()
class InactiveAccountException : Exception()
class NotDriverException : Exception()
class InvalidCredentialsException : Exception()


