class UpdatePhoneResponse {
  final bool success;
  final String? message;
  final UpdatePhoneData? data;

  const UpdatePhoneResponse({
    required this.success,
    this.message,
    this.data,
  });

  factory UpdatePhoneResponse.fromJson(Map<String, dynamic> json) {
    return UpdatePhoneResponse(
      success: json['success'] as bool? ?? false,
      message: json['message'] as String?,
      data: json['data'] != null
          ? UpdatePhoneData.fromJson(Map<String, dynamic>.from(json['data']))
          : null,
    );
  }
}

class UpdatePhoneData {
  final int id;
  final String phone;

  const UpdatePhoneData({
    required this.id,
    required this.phone,
  });

  factory UpdatePhoneData.fromJson(Map<String, dynamic> json) {
    return UpdatePhoneData(
      id: json['id'] as int? ?? 0,
      phone: json['phone'] as String? ?? '',
    );
  }
}
