package app.mask.control

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import app.mask.control.databinding.ActivityMainBinding
import com.esafirm.imagepicker.features.ImagePicker

class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        binding.buttonPhoto.setOnClickListener {
            ImagePicker.create(this)
                .single()
                .showCamera(true)
                .imageDirectory("Camera")
                .start()
        }

        viewModel.drawableLiveData.observe(this) { drawable ->
            binding.imagePhoto.setImageDrawable(drawable)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (ImagePicker.shouldHandle(requestCode, resultCode, data)) {
            ImagePicker.getFirstImageOrNull(data)
                ?.let { image -> viewModel.onActivityResult(this, image) }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }
}
