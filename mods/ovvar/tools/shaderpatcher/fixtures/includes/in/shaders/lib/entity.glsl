uniform sampler2D gtexture;

#ifdef FRAGMENT_STAGE

in vec2 texcoord;
in vec4 glcolor;

void main() {
	vec4 albedo = texture(gtexture, texcoord) * glcolor;
	gl_FragData[0] = albedo;
}

#endif

#ifdef VERTEX_STAGE

out vec2 texcoord;
out vec4 glcolor;

void main() {
	gl_Position = ftransform();
	texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
	glcolor = gl_Color;
}

#endif
