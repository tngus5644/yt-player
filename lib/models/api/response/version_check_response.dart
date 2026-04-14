class VersionCheckResponse {
  final bool success;
  final String? message;
  final VersionCheckData? data;

  const VersionCheckResponse({
    required this.success,
    this.message,
    this.data,
  });

  factory VersionCheckResponse.fromJson(Map<String, dynamic> json) {
    return VersionCheckResponse(
      success: json['success'] as bool? ?? false,
      message: json['message'] as String?,
      data: json['data'] != null
          ? VersionCheckData.fromJson(Map<String, dynamic>.from(json['data']))
          : null,
    );
  }
}

class VersionCheckData {
  final bool isUpdateRequired;
  final String? latestVersion;
  final String? updateUrl;
  final String? message;

  const VersionCheckData({
    required this.isUpdateRequired,
    this.latestVersion,
    this.updateUrl,
    this.message,
  });

  factory VersionCheckData.fromJson(Map<String, dynamic> json) {
    return VersionCheckData(
      isUpdateRequired: json['is_update_required'] as bool? ?? false,
      latestVersion: json['latest_version'] as String?,
      updateUrl: json['update_url'] as String?,
      message: json['message'] as String?,
    );
  }
}
