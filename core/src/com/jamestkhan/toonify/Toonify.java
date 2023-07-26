package com.jamestkhan.toonify;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.FPSLogger;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.loader.G3dModelLoader;
import com.badlogic.gdx.graphics.g3d.shaders.DepthShader;
import com.badlogic.gdx.graphics.g3d.utils.DepthShaderProvider;
import com.badlogic.gdx.graphics.g3d.utils.FirstPersonCameraController;
import com.badlogic.gdx.graphics.g3d.utils.TextureBinder;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.UBJsonReader;

public class Toonify extends ApplicationAdapter {
    private ModelBatch modelBatch;
    private ModelInstance instance;
    private Environment environment;
    private PerspectiveCamera camera;

    private FrameBuffer toonColorFramebuffer;
    private FrameBuffer depthframebuffer;
    private FrameBuffer sceneFramebuffer;

    private ShaderProgram toonifyshader;
    private ShaderProgram outlineShader;
    private ShaderProgram combineShader;

    private Mesh fullScreenQuad;
    private ModelBatch depthModelBatch;

    private FirstPersonCameraController controller;

    private FPSLogger fpsLogger;


    @Override
    public void create() {
        fpsLogger = new FPSLogger();

        camera = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.position.set(5f, 5f, 5f);
        camera.lookAt(0, 0, 0);
        camera.near = 1f;
        camera.far = 1000f;
        camera.up.set(0, 1, 0);
        camera.update();

        controller = new FirstPersonCameraController(camera);
        Gdx.input.setInputProcessor(controller);

        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.4f, 0.4f, 0.4f, 1.f));

        DirectionalLight light = new DirectionalLight();
        light.set(0.8f, 0.8f, 0.8f, -0.5f, -1.0f, -0.8f);
        environment.add(light);

        modelBatch = new ModelBatch();

        DepthShader.Config config = new DepthShader.Config();
        config.defaultCullFace = GL20.GL_BACK;

        // The tutorial uses a custom vertex shader that takes in camera near and far using eye space coordinates
        // you can mimmick the tutorial by uncommenting this line however I had better results with
        // default depth shader for this simple scene
        //config.vertexShader = Gdx.files.internal("shaders/depth.vertex.glsl").readString();

        depthModelBatch = new ModelBatch(new DepthShaderProvider(config));

        // load model
        G3dModelLoader loader = new G3dModelLoader(new UBJsonReader());
        instance = new ModelInstance(loader.loadModel(Gdx.files.internal("teapot.g3db")));

        sceneFramebuffer = new FrameBuffer(Pixmap.Format.RGBA8888, Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);
        toonColorFramebuffer = new FrameBuffer(Pixmap.Format.RGBA8888, Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), false);
        depthframebuffer = new FrameBuffer(Pixmap.Format.RGBA8888, Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);

        toonifyshader = new ShaderProgram(
                Gdx.files.internal("shaders/toonify.vertex.glsl")
                , Gdx.files.internal("shaders/toonify.fragment.glsl"));

        outlineShader = new ShaderProgram(
                Gdx.files.internal("shaders/outline.vertex.glsl")
                , Gdx.files.internal("shaders/outline.fragment.glsl"));

        combineShader = new ShaderProgram(
                Gdx.files.internal("shaders/combine.vertex.glsl")
                , Gdx.files.internal("shaders/combine.fragment.glsl"));

        fullScreenQuad = createFullScreenQuad();
    }

    @Override
    public void render() {
        controller.update(Gdx.graphics.getDeltaTime());

        TextureBinder textureBinder = modelBatch.getRenderContext().textureBinder;

        // Capture scene
        sceneFramebuffer.begin();
        renderScene(modelBatch);
        sceneFramebuffer.end();

        // Render toonify shader
        sceneFramebuffer.getColorBufferTexture().bind(0);
        toonColorFramebuffer.begin();
        ScreenUtils.clear(Color.CLEAR);
        toonifyshader.bind();
        fullScreenQuad.render(toonifyshader, GL20.GL_TRIANGLE_FAN, 0, 4);
        toonColorFramebuffer.end();

        // Render depth
        depthframebuffer.begin();
        renderScene(depthModelBatch);
        depthframebuffer.end();

        // Render outline depth
        sceneFramebuffer.begin();
        ScreenUtils.clear(Color.CLEAR, true);
        outlineShader.bind();
        int unit = textureBinder.bind(depthframebuffer.getColorBufferTexture());
        outlineShader.setUniformi("u_depthTexture", unit);
        outlineShader.setUniformf("u_size", Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        fullScreenQuad.render(outlineShader, GL20.GL_TRIANGLE_FAN);
        sceneFramebuffer.end();

        // Final render, combine outline + toon color
        ScreenUtils.clear(Color.CLEAR, true);
        combineShader.bind();
        unit = textureBinder.bind(sceneFramebuffer.getColorBufferTexture());
        combineShader.setUniformi("u_colorTexture", unit);
        unit = textureBinder.bind(toonColorFramebuffer.getColorBufferTexture());
        combineShader.setUniformi("u_outlineTexture", unit);
        fullScreenQuad.render(combineShader, GL20.GL_TRIANGLE_FAN);

        fpsLogger.log();
    }

    private void renderScene(ModelBatch batch) {
        ScreenUtils.clear(Color.CLEAR, true);
        batch.begin(camera);
        batch.render(instance, environment);
        batch.end();
    }

    public Mesh createFullScreenQuad() {

        float[] verts = new float[20];
        int i = 0;

        verts[i++] = -1;
        verts[i++] = -1;
        verts[i++] = 0;
        verts[i++] = 0f;
        verts[i++] = 0f;

        verts[i++] = 1f;
        verts[i++] = -1;
        verts[i++] = 0;
        verts[i++] = 1f;
        verts[i++] = 0f;

        verts[i++] = 1f;
        verts[i++] = 1f;
        verts[i++] = 0;
        verts[i++] = 1f;
        verts[i++] = 1f;

        verts[i++] = -1;
        verts[i++] = 1f;
        verts[i++] = 0;
        verts[i++] = 0f;
        verts[i++] = 1f;

        Mesh mesh = new Mesh(true, 4, 0, new VertexAttribute(VertexAttributes.Usage.Position, 3, ShaderProgram.POSITION_ATTRIBUTE),
                new VertexAttribute(VertexAttributes.Usage.TextureCoordinates, 2, ShaderProgram.TEXCOORD_ATTRIBUTE + "0"));

        mesh.setVertices(verts);
        return mesh;
    }

    @Override
    public void dispose() {
        modelBatch.dispose();
    }
}
