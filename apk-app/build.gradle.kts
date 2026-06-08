// Mẫu cấu hình signingConfigs cho file build.gradle.kts (Kotlin DSL)
// Bạn hãy sử dụng mẫu này cho dự án phát triển trên máy tính (Android Studio) của bạn.
// (Lưu ý: Môi trường build trên AI Studio hiện tại không hỗ trợ thay đổi cấu hình Signing mặc định).

android {
    signingConfigs {
        create("release") {
            // Đường dẫn đến file tệp khóa .p12 của bạn
            storeFile = file("/storage/emulated/0/MT2/apks/Key12.p12")
            // Khai báo loại kho khóa là PKCS12 thay vì JKS mặc định
            storeType = "PKCS12"
            storePassword = "huynhgia_long1234"
            keyAlias = "longvn"
            keyPassword = "huynhgia_long1234"
        }
    }
    
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}
