#ifdef GL_ES
#define LOWP lowp
#define MED mediump
precision mediump float;
#else
#define LOWP
#define MED
#endif

uniform sampler2D u_colorTexture;
uniform sampler2D u_outlineTexture;
varying vec2 v_texCoord;

void main() {
    vec4 color = texture2D(u_colorTexture, v_texCoord);
    vec4 depth = texture2D(u_outlineTexture, v_texCoord);

    gl_FragColor = mix(color, depth, depth.a);
}