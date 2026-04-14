class AuthResponse {
  final bool success;
  final String? token;
  final AuthUserData? user;
  final String? message;

  const AuthResponse({
    required this.success,
    this.token,
    this.user,
    this.message,
  });

  factory AuthResponse.fromJson(Map<String, dynamic> json) {
    return AuthResponse(
      success: json['success'] as bool? ?? false,
      token: json['token'] as String?,
      user: json['user'] != null
          ? AuthUserData.fromJson(Map<String, dynamic>.from(json['user']))
          : null,
      message: json['message'] as String?,
    );
  }
}

class AuthUserData {
  final int id;
  final String name;
  final String email;
  final String? profilePhotoUrl;

  const AuthUserData({
    required this.id,
    required this.name,
    required this.email,
    this.profilePhotoUrl,
  });

  factory AuthUserData.fromJson(Map<String, dynamic> json) {
    return AuthUserData(
      id: json['id'] as int? ?? 0,
      name: json['name'] as String? ?? '',
      email: json['email'] as String? ?? '',
      profilePhotoUrl: json['profile_photo_url'] as String?,
    );
  }
}
