# RGB colour, client screenshots

Cloud client run (Xvfb, software GL via `SDL_VIDEO_FORCE_EGL=1`) of `RgbClientTests` from branch
`moredyes-rainbow`. One shot per subject at `t0` and again at `t45s` (about 900 ticks, a quarter of
the 3600-tick cycle, so about 90 degrees of hue). Mean hue is over the coloured pixels of each shot
(grey wall and floor excluded); measured gap between the two shots was 907 ticks.

| Region | t0 | t45s | Change | |
|---|---|---|---|---|
| wool | 22.9 | 114.4 | +91.5 | animates |
| concrete | 13.5 | 115.7 | +102.2 | animates |
| stained glass | 16.0 | 116.8 | +100.7 | animates |
| shulker box item | 16.8 | 122.1 | +105.2 | animates |
| banner | 28.6 | 117.2 | +88.6 | animates |
| armour stand (leather) | 40.5 | 139.5 | +99.0 | animates |
| llama carpet | 34.4 | 35.2 | +0.8 | static: red / green / blue stripes |
| happy ghast harness | 208.8 | 208.8 | 0.0 | static: red / green / blue stripes |
| overview (all subjects) | 154.5 | 155.3 | +0.8 | dominated by sky and grass, not meaningful |

The llama carpet and harness are equipment textures, which no atlas animates, so they hold three
static horizontal bands (red, green, blue) over the visible rows of the texture. In the shots the
llama shows red on the back, green on the side and blue on the chest straps; the harness shows blue
goggles, green sides and red on the back.

The overview shot looks along +z, so the row is mirrored: wool is at the right, the ghast at the left.

Files: `rgb_<subject>_t0.png`, `rgb_<subject>_t45s.png`.
