class AdTrackingResponse {
  final bool success;
  final List<String> data;

  const AdTrackingResponse({
    required this.success,
    this.data = const [],
  });

  factory AdTrackingResponse.fromJson(Map<String, dynamic> json) {
    return AdTrackingResponse(
      success: json['success'] as bool? ?? false,
      data: (json['data'] as List<dynamic>?)
              ?.map((e) => e as String)
              .toList() ??
          [],
    );
  }
}
