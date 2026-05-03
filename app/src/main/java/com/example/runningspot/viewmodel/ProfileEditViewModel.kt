package com.example.runningspot.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class ProfileEditState(
    val profileUrl: String? = null,
    val isUploading: Boolean = false,
    val isSaving: Boolean = false,
    val message: String? = null,
    val saveSuccess: Boolean = false
)

class ProfileEditViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    private val _state = MutableStateFlow(ProfileEditState())
    val state: StateFlow<ProfileEditState> = _state

    fun setInitialProfileUrl(url: String?) {
        if (_state.value.profileUrl.isNullOrBlank()) {
            _state.value = _state.value.copy(profileUrl = url)
        }
    }

    fun uploadProfileImage(context: Context, uri: Uri) {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            _state.value = _state.value.copy(message = "로그인이 필요합니다")
            return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(isUploading = true, message = null)

            runCatching {
                val compressedFile = compressProfileImage(context.applicationContext, uri)

                val ref = storage.reference
                    .child("profileImages/$uid/profile_${System.currentTimeMillis()}.jpg")

                ref.putFile(Uri.fromFile(compressedFile)).await()
                ref.downloadUrl.await().toString()
            }.onSuccess { downloadUrl ->
                _state.value = _state.value.copy(
                    profileUrl = downloadUrl,
                    isUploading = false,
                    message = "이미지 업로드 완료"
                )
            }.onFailure {
                _state.value = _state.value.copy(
                    isUploading = false,
                    message = "프로필 이미지 업로드 실패"
                )
            }
        }
    }

    private suspend fun compressProfileImage(context: Context, uri: Uri): File =
        withContext(Dispatchers.IO) {
            val originalBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = false
                }
            } else {
                context.contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(BitmapFactory.decodeStream(input)) {
                        "이미지를 읽을 수 없습니다"
                    }
                }
            }

            val maxSize = 512
            val ratio = minOf(
                maxSize.toFloat() / originalBitmap.width,
                maxSize.toFloat() / originalBitmap.height,
                1f
            )

            val targetWidth = (originalBitmap.width * ratio).toInt().coerceAtLeast(1)
            val targetHeight = (originalBitmap.height * ratio).toInt().coerceAtLeast(1)

            val resizedBitmap =
                if (targetWidth == originalBitmap.width && targetHeight == originalBitmap.height) {
                    originalBitmap
                } else {
                    Bitmap.createScaledBitmap(originalBitmap, targetWidth, targetHeight, true)
                }

            val outputFile = File(context.cacheDir, "profile_${System.currentTimeMillis()}.jpg")
            FileOutputStream(outputFile).use { output ->
                resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 75, output)
            }

            if (resizedBitmap != originalBitmap) {
                resizedBitmap.recycle()
            }
            originalBitmap.recycle()

            outputFile
        }

    fun saveProfile(
        nickname: String,
        birthYear: Int,
        birthMonth: Int,
        birthDay: Int,
        gender: String,
        heightCm: Int,
        weightKg: Double,
        dailyGoalKm: Double
    ) {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            _state.value = _state.value.copy(message = "로그인이 필요합니다")
            return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(
                isSaving = true,
                message = null,
                saveSuccess = false
            )

            runCatching {
                val data = mutableMapOf<String, Any>(
                    "nickname" to nickname.trim(),
                    "birthDate" to "%04d-%02d-%02d".format(birthYear, birthMonth, birthDay),
                    "birthYear" to birthYear,
                    "gender" to gender,
                    "heightCm" to heightCm.toDouble(),
                    "weightKg" to weightKg,
                    "dailyGoalKm" to dailyGoalKm,
                    "updatedAt" to FieldValue.serverTimestamp()
                )

                _state.value.profileUrl
                    ?.takeIf { it.isNotBlank() }
                    ?.let { data["profileUrl"] = it }

                db.collection("users")
                    .document(uid)
                    .set(data, SetOptions.merge())
                    .await()
            }.onSuccess {
                _state.value = _state.value.copy(
                    isSaving = false,
                    saveSuccess = true,
                    message = "프로필 저장 완료"
                )
            }.onFailure {
                _state.value = _state.value.copy(
                    isSaving = false,
                    message = "저장 실패"
                )
            }
        }
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
    }

    fun clearSaveSuccess() {
        _state.value = _state.value.copy(saveSuccess = false)
    }
}