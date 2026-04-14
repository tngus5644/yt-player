class CountResponse {
  final bool success;
  final String? message;

  const CountResponse({
    required this.success,
    this.message,
  });

  factory CountResponse.fromJson(Map<String, dynamic> json) {
    return CountResponse(
      success: json['success'] as bool? ?? false,
      message: json['message'] as String?,
    );
  }
}
