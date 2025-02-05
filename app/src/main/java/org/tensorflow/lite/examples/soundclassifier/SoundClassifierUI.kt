package org.tensorflow.lite.examples.soundclassifier

interface SoundClassifierUI
{
  fun ignoreMeta(): Boolean
  fun isShowingProgress(): Boolean

  fun setLocationText(lat: Float, lon: Float)
  fun setPrimaryText(text: String)
  fun setPrimaryText(value: Float, label: String)
  fun setSecondaryText(text: String)
  fun setSecondaryText(value: Float, label: String)

  fun showImages(): Boolean
  fun getImageURL(): String?
  fun showImage(label: String, url: String?)
  fun hideImage()
}