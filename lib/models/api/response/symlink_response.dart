class SymlinkResponse {
  final bool success;
  final List<String> data;

  const SymlinkResponse({
    required this.success,
    this.data = const [],
  });

  factory SymlinkResponse.fromJson(Map<String, dynamic> json) {
    return SymlinkResponse(
      success: json['success'] as bool? ?? false,
      data: (json['data'] as List<dynamic>?)
              ?.map((e) => e as String)
              .toList() ??
          [],
    );
  }
}
