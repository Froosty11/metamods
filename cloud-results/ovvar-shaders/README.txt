Runs a-d NOT executed to completion: the client cannot create an OpenGL context in this cloud container.
Minecraft 26.3 (SDL3) under Xvfb (Mesa llvmpipe, glxinfo works, GLX 1.4, 24/32-bit visuals with depth24/stencil8 present) fails at startup:
  BackendCreationException: Failed to create window for OpenGL context: Couldn't find matching GLX visual
  Vulkan: Installed Vulkan doesn't implement VK_KHR_surface
Tried: -screen 0 1920x1080x24, x24+32 with +extension GLX +render -noreset, XDG_RUNTIME_DIR, LIBGL_ALWAYS_SOFTWARE=1, SDL_VIDEODRIVER=x11, SDL_VIDEO_FORCE_EGL=1, MESA_GL_VERSION_OVERRIDE=4.5. Same error each time.
Succeeded: network check, datagen, patcherJar, ovvar-shaderpatch build, BSL v10.1.8 download, patcher -> bsl+ovvar.zip.
Log tail of last attempt: a_log_tail.txt
