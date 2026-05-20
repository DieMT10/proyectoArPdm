/*
 * Copyright 2020 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.proyectoarpdm.common.samplerender;

import android.opengl.GLES30;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import de.javagl.obj.Obj;
import de.javagl.obj.ObjData;
import de.javagl.obj.ObjReader;
import de.javagl.obj.ObjUtils;
import java.io.Closeable;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * A collection of vertices, faces, and other attributes that define how to render a 3D object.
 *
 * <p>To render the mesh, use {@link SampleRender#draw()}.
 */
public class Mesh implements Closeable {
    private static final String TAG = Mesh.class.getSimpleName();

    /**
     * The kind of primitive to render.
     *
     * <p>This determines how the data in {@link VertexBuffer}s are interpreted. See <a
     * href="https://www.khronos.org/opengl/wiki/Primitive">here</a> for more on how primitives
     * behave.
     */
    public enum PrimitiveMode {
        POINTS(GLES30.GL_POINTS),
        LINE_STRIP(GLES30.GL_LINE_STRIP),
        LINE_LOOP(GLES30.GL_LINE_LOOP),
        LINES(GLES30.GL_LINES),
        TRIANGLE_STRIP(GLES30.GL_TRIANGLE_STRIP),
        TRIANGLE_FAN(GLES30.GL_TRIANGLE_FAN),
        TRIANGLES(GLES30.GL_TRIANGLES);

        /* package-private */
        final int glesEnum;

        private PrimitiveMode(int glesEnum) {
            this.glesEnum = glesEnum;
        }
    }

    private final int[] vertexArrayId = {0};
    private final PrimitiveMode primitiveMode;
    private final IndexBuffer indexBuffer;
    private final VertexBuffer[] vertexBuffers;
    private Texture modelTexture;

    /**
     * Construct a {@link Mesh}.
     *
     * <p>The data in the given {@link IndexBuffer} and {@link VertexBuffer}s does not need to be
     * finalized; they may be freely changed throughout the lifetime of a {@link Mesh} using their
     * respective {@code set()} methods.
     *
     * <p>The ordering of the {@code vertexBuffers} is significant. Their array indices will
     * correspond to their attribute locations, which must be taken into account in shader code. The
     * <a href="https://www.khronos.org/opengl/wiki/Layout_Qualifier_(GLSL)">layout qualifier</a> must
     * be used in the vertex shader code to explicitly associate attributes with these indices.
     */
    public Mesh(
            SampleRender render,
            PrimitiveMode primitiveMode,
            IndexBuffer indexBuffer,
            VertexBuffer[] vertexBuffers) {
        if (vertexBuffers == null || vertexBuffers.length == 0) {
            throw new IllegalArgumentException("Must pass at least one vertex buffer");
        }

        this.primitiveMode = primitiveMode;
        this.indexBuffer = indexBuffer;
        this.vertexBuffers = vertexBuffers;

        try {

            GLES30.glGenVertexArrays(1, vertexArrayId, 0);
            GLError.maybeThrowGLException("Failed to generate a vertex array", "glGenVertexArrays");


            GLES30.glBindVertexArray(vertexArrayId[0]);
            GLError.maybeThrowGLException("Failed to bind vertex array object", "glBindVertexArray");

            if (indexBuffer != null) {
                GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, indexBuffer.getBufferId());
                GLError.maybeThrowGLException("Failed to bind index buffer", "glBindBuffer");
            }

            for (int i = 0; i < vertexBuffers.length; ++i) {
                GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vertexBuffers[i].getBufferId());
                GLError.maybeThrowGLException("Failed to bind vertex buffer", "glBindBuffer");
                GLES30.glVertexAttribPointer(
                        i, vertexBuffers[i].getNumberOfEntriesPerVertex(), GLES30.GL_FLOAT, false, 0, 0);
                GLError.maybeThrowGLException(
                        "Failed to associate vertex buffer with vertex array", "glVertexAttribPointer");
                GLES30.glEnableVertexAttribArray(i);
                GLError.maybeThrowGLException(
                        "Failed to enable vertex buffer", "glEnableVertexAttribArray");
            }

            GLES30.glBindVertexArray(0);
            GLError.maybeThrowGLException("Failed to unbind vertex array", "glBindVertexArray");
        } catch (Throwable t) {
            close();
            throw t;
        }
    }

    /**
     * Constructs a {@link Mesh} from the given asset file.
     * Supports .obj and .glb (glTF binary) formats.
     */
    public static Mesh createFromAsset(SampleRender render, String assetFileName) throws IOException {
        if (assetFileName.toLowerCase().endsWith(".obj")) {
            return createFromObjAsset(render, assetFileName);
        } else if (assetFileName.toLowerCase().endsWith(".glb")) {
            return createFromGlbAsset(render, assetFileName);
        } else {
            throw new IllegalArgumentException("Unsupported file extension: " + assetFileName);
        }
    }

    /**
     * Constructs a list of {@link Mesh} objects from the given asset file.
     * Supports .glb (glTF binary) format with multiple primitives/materials.
     */
    public static List<Mesh> createMeshesFromAsset(SampleRender render, String assetFileName) throws IOException {
        try (InputStream inputStream = render.getAssets().open(assetFileName)) {
            if (assetFileName.toLowerCase().endsWith(".glb")) {
                return createMeshesFromGlbStream(render, inputStream);
            } else {
                // Fallback to single mesh for other formats
                return Collections.singletonList(createFromAsset(render, assetFileName));
            }
        }
    }

    public static List<Mesh> createMeshesFromFile(SampleRender render, String filePath) throws IOException {
        try (InputStream inputStream = new FileInputStream(filePath)) {
            if (filePath.toLowerCase().endsWith(".glb")) {
                return createMeshesFromGlbStream(render, inputStream);
            } else {
                return Collections.singletonList(createFromFile(render, filePath));
            }
        }
    }

    private static List<Mesh> createMeshesFromGlbStream(SampleRender render, InputStream inputStream) throws IOException {
        try {
            byte[] glbData = readAllBytes(inputStream);
            ByteBuffer buffer = ByteBuffer.wrap(glbData).order(ByteOrder.LITTLE_ENDIAN);

            // GLB Header
            int magic = buffer.getInt();
            if (magic != 0x46546C67) throw new IOException("Invalid GLB magic");
            int version = buffer.getInt();
            int length = buffer.getInt();

            // JSON Chunk
            int jsonChunkLength = buffer.getInt();
            int jsonChunkType = buffer.getInt();
            byte[] jsonBytes = new byte[jsonChunkLength];
            buffer.get(jsonBytes);
            JSONObject json = new JSONObject(new String(jsonBytes, StandardCharsets.UTF_8));

            // BIN Chunk
            int binChunkLength = buffer.getInt();
            int binChunkType = buffer.getInt();
            ByteBuffer binBuffer = buffer.slice().order(ByteOrder.LITTLE_ENDIAN);

            List<Mesh> allMeshes = new ArrayList<>();
            JSONArray meshesJson = json.getJSONArray("meshes");
            
            for (int i = 0; i < meshesJson.length(); i++) {
                JSONObject meshJson = meshesJson.getJSONObject(i);
                JSONArray primitivesJson = meshJson.getJSONArray("primitives");
                
                for (int j = 0; j < primitivesJson.length(); j++) {
                    JSONObject primitiveJson = primitivesJson.getJSONObject(j);
                    allMeshes.add(createSingleMeshFromPrimitive(render, json, binBuffer, primitiveJson));
                }
            }
            return allMeshes;
        } catch (Exception e) {
            throw new IOException("Failed to parse GLB", e);
        }
    }

    private static Mesh createSingleMeshFromPrimitive(SampleRender render, JSONObject json, ByteBuffer binBuffer, JSONObject primitiveJson) throws JSONException {
        JSONObject attributes = primitiveJson.getJSONObject("attributes");

        // Primitive Mode
        int mode = primitiveJson.optInt("mode", 4);
        Mesh.PrimitiveMode primitiveMode = Mesh.PrimitiveMode.TRIANGLES;
        // (Misma lógica de switch que antes para mode...)

        // Positions
        FloatBuffer localCoordinates = extractFloatBuffer(json, binBuffer, attributes.getInt("POSITION"), 3);

        // TexCoords
        FloatBuffer textureCoordinates;
        if (attributes.has("TEXCOORD_0")) {
            textureCoordinates = extractFloatBuffer(json, binBuffer, attributes.getInt("TEXCOORD_0"), 2);
        } else {
            textureCoordinates = ByteBuffer.allocateDirect(localCoordinates.limit() / 3 * 2 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        }

        // Normals
        FloatBuffer normals;
        if (attributes.has("NORMAL")) {
            normals = extractFloatBuffer(json, binBuffer, attributes.getInt("NORMAL"), 3);
        } else {
            normals = ByteBuffer.allocateDirect(localCoordinates.limit() * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        }

        // Indices
        IndexBuffer indexBuffer = null;
        if (primitiveJson.has("indices")) {
            indexBuffer = new IndexBuffer(render, extractIntBuffer(json, binBuffer, primitiveJson.getInt("indices")));
        }

        Mesh mesh = new Mesh(render, primitiveMode, indexBuffer, new VertexBuffer[]{
                new VertexBuffer(render, 3, localCoordinates),
                new VertexBuffer(render, 2, textureCoordinates),
                new VertexBuffer(render, 3, normals)
        });

        // EXTRACT COLOR/TEXTURE FOR THIS SPECIFIC PRIMITIVE
        try {
            float[] color = {1f, 1f, 1f, 1f};
            int matIdx = primitiveJson.optInt("material", -1);
            if (matIdx != -1 && json.has("materials")) {
                JSONObject mat = json.getJSONArray("materials").getJSONObject(matIdx);
                if (mat.has("pbrMetallicRoughness")) {
                    JSONObject pbr = mat.getJSONObject("pbrMetallicRoughness");
                    if (pbr.has("baseColorFactor")) {
                        JSONArray colArr = pbr.getJSONArray("baseColorFactor");
                        for (int k = 0; k < 4; k++) color[k] = (float) colArr.getDouble(k);
                    }
                }
            }
            
            // Crear textura de color sólido como base
            Bitmap bmp = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
            bmp.setPixel(0, 0, android.graphics.Color.argb((int)(color[3]*255), (int)(color[0]*255), (int)(color[1]*255), (int)(color[2]*255)));
            mesh.setModelTexture(Texture.createFromBitmap(render, bmp, Texture.WrapMode.CLAMP_TO_EDGE, Texture.ColorFormat.SRGB));
            
        } catch (Exception e) {
            Log.e(TAG, "Error setting primitive material", e);
        }

        return mesh;
    }

    private static Mesh createFromObjAsset(SampleRender render, String assetFileName) throws IOException {
        try (InputStream inputStream = render.getAssets().open(assetFileName)) {
            return createFromObjStream(render, inputStream);
        }
    }

    private static Mesh createFromObjStream(SampleRender render, InputStream inputStream) throws IOException {
        Obj obj = ObjUtils.convertToRenderable(ObjReader.read(inputStream));

        // Obtain the data from the OBJ, as direct buffers:
        IntBuffer vertexIndices = ObjData.getFaceVertexIndices(obj, /*numVerticesPerFace=*/ 3);
        FloatBuffer localCoordinates = ObjData.getVertices(obj);
        FloatBuffer textureCoordinates = ObjData.getTexCoords(obj, /*dimensions=*/ 2);
        FloatBuffer normals = ObjData.getNormals(obj);

        VertexBuffer[] vertexBuffers = {
                new VertexBuffer(render, 3, localCoordinates),
                new VertexBuffer(render, 2, textureCoordinates),
                new VertexBuffer(render, 3, normals),
        };

        IndexBuffer indexBuffer = new IndexBuffer(render, vertexIndices);

        return new Mesh(render, Mesh.PrimitiveMode.TRIANGLES, indexBuffer, vertexBuffers);
    }

    private static Mesh createFromGlbAsset(SampleRender render, String assetFileName) throws IOException {
        try (InputStream inputStream = render.getAssets().open(assetFileName)) {
            return createFromGlbStream(render, inputStream);
        }
    }

    private static Mesh createFromGlbStream(SampleRender render, InputStream inputStream) throws IOException {
        try {
            byte[] glbData = readAllBytes(inputStream);
            ByteBuffer buffer = ByteBuffer.wrap(glbData).order(ByteOrder.LITTLE_ENDIAN);

            // GLB Header
            int magic = buffer.getInt();
            if (magic != 0x46546C67) throw new IOException("Invalid GLB magic: " + magic);
            int version = buffer.getInt();
            if (version != 2) throw new IOException("Unsupported GLB version: " + version);
            int length = buffer.getInt();

            // JSON Chunk
            int jsonChunkLength = buffer.getInt();
            int jsonChunkType = buffer.getInt();
            if (jsonChunkType != 0x4E4F534A) throw new IOException("Invalid JSON chunk type: " + jsonChunkType);
            byte[] jsonBytes = new byte[jsonChunkLength];
            buffer.get(jsonBytes);
            String jsonString = new String(jsonBytes, StandardCharsets.UTF_8);
            JSONObject json = new JSONObject(jsonString);

            // BIN Chunk
            int binChunkLength = buffer.getInt();
            int binChunkType = buffer.getInt();
            if (binChunkType != 0x004E4942) throw new IOException("Invalid BIN chunk type: " + binChunkType);
            ByteBuffer binBuffer = buffer.slice().order(ByteOrder.LITTLE_ENDIAN);

            return createFromGltfJson(render, json, binBuffer);
        } catch (JSONException e) {
            throw new IOException("Failed to parse GLB JSON", e);
        }
    }

    private static Mesh createFromGltfAsset(SampleRender render, String assetFileName) throws IOException {
        // Basic glTF (non-binary) support assumes a single .bin file with the same name.
        try (InputStream inputStream = render.getAssets().open(assetFileName)) {
            byte[] jsonBytes = readAllBytes(inputStream);
            String jsonString = new String(jsonBytes, StandardCharsets.UTF_8);
            JSONObject json = new JSONObject(jsonString);

            // Load the first buffer (usually the .bin file)
            JSONArray buffers = json.getJSONArray("buffers");
            String binUri = buffers.getJSONObject(0).getString("uri");
            
            // Resolve relative path for the .bin file
            String binPath;
            int lastSlash = assetFileName.lastIndexOf('/');
            if (lastSlash != -1) {
                binPath = assetFileName.substring(0, lastSlash + 1) + binUri;
            } else {
                binPath = binUri;
            }

            try (InputStream binInputStream = render.getAssets().open(binPath)) {
                byte[] binData = readAllBytes(binInputStream);
                ByteBuffer binBuffer = ByteBuffer.wrap(binData).order(ByteOrder.LITTLE_ENDIAN);
                return createFromGltfJson(render, json, binBuffer);
            }
        } catch (JSONException e) {
            throw new IOException("Failed to parse glTF JSON", e);
        }
    }

    private static Mesh createFromGltfJson(SampleRender render, JSONObject json, ByteBuffer binBuffer) throws JSONException {
        JSONArray meshes = json.getJSONArray("meshes");
        JSONObject mesh0 = meshes.getJSONObject(0);
        JSONArray primitives = mesh0.getJSONArray("primitives");
        JSONObject primitive0 = primitives.getJSONObject(0);
        JSONObject attributes = primitive0.getJSONObject("attributes");

        // Primitive Mode
        int mode = primitive0.optInt("mode", 4); // Default to TRIANGLES
        Mesh.PrimitiveMode primitiveMode;
        switch (mode) {
            case 0: primitiveMode = Mesh.PrimitiveMode.POINTS; break;
            case 1: primitiveMode = Mesh.PrimitiveMode.LINE_STRIP; break;
            case 2: primitiveMode = Mesh.PrimitiveMode.LINE_LOOP; break;
            case 3: primitiveMode = Mesh.PrimitiveMode.LINES; break;
            case 4: primitiveMode = Mesh.PrimitiveMode.TRIANGLES; break;
            case 5: primitiveMode = Mesh.PrimitiveMode.TRIANGLE_STRIP; break;
            case 6: primitiveMode = Mesh.PrimitiveMode.TRIANGLE_FAN; break;
            default: primitiveMode = Mesh.PrimitiveMode.TRIANGLES; break;
        }

        // Positions
        int posAccessorIdx = attributes.getInt("POSITION");
        FloatBuffer localCoordinates = extractFloatBuffer(json, binBuffer, posAccessorIdx, 3);

        // Texture Coordinates
        FloatBuffer textureCoordinates;
        if (attributes.has("TEXCOORD_0")) {
            int texAccessorIdx = attributes.getInt("TEXCOORD_0");
            textureCoordinates = extractFloatBuffer(json, binBuffer, texAccessorIdx, 2);
        } else {
            textureCoordinates = ByteBuffer.allocateDirect(localCoordinates.limit() / 3 * 2 * 4)
                    .order(ByteOrder.nativeOrder()).asFloatBuffer();
        }

        // Normals
        FloatBuffer normals;
        if (attributes.has("NORMAL")) {
            int normAccessorIdx = attributes.getInt("NORMAL");
            normals = extractFloatBuffer(json, binBuffer, normAccessorIdx, 3);
        } else {
            normals = ByteBuffer.allocateDirect(localCoordinates.limit() * 4)
                    .order(ByteOrder.nativeOrder()).asFloatBuffer();
        }

        // Indices
        IndexBuffer indexBuffer = null;
        if (primitive0.has("indices")) {
            int indicesAccessorIdx = primitive0.getInt("indices");
            IntBuffer vertexIndices = extractIntBuffer(json, binBuffer, indicesAccessorIdx);
            indexBuffer = new IndexBuffer(render, vertexIndices);
        }

        VertexBuffer[] vertexBuffers = {
                new VertexBuffer(render, 3, localCoordinates),
                new VertexBuffer(render, 2, textureCoordinates),
                new VertexBuffer(render, 3, normals),
        };

        Mesh mesh = new Mesh(render, primitiveMode, indexBuffer, vertexBuffers);

        // Try to extract material color or texture
        try {
            // 1. Check for material color
            float[] baseColorFactor = {1.0f, 1.0f, 1.0f, 1.0f}; // Default white
            int materialIdx = primitive0.optInt("material", -1);
            if (materialIdx != -1 && json.has("materials")) {
                JSONObject material = json.getJSONArray("materials").getJSONObject(materialIdx);
                if (material.has("pbrMetallicRoughness")) {
                    JSONObject pbr = material.getJSONObject("pbrMetallicRoughness");
                    if (pbr.has("baseColorFactor")) {
                        JSONArray colorArr = pbr.getJSONArray("baseColorFactor");
                        for (int i = 0; i < 4; i++) {
                            baseColorFactor[i] = (float) colorArr.getDouble(i);
                        }
                    }
                }
            }

            // 2. Check for texture
            boolean textureFound = false;
            if (json.has("images") && json.getJSONArray("images").length() > 0) {
                JSONObject image0 = json.getJSONArray("images").getJSONObject(0);
                if (image0.has("bufferView")) {
                    int bvIdx = image0.getInt("bufferView");
                    JSONObject bv = json.getJSONArray("bufferViews").getJSONObject(bvIdx);
                    int offset = bv.optInt("byteOffset", 0);
                    int length = bv.getInt("byteLength");

                    byte[] imgData = new byte[length];
                    int oldPos = binBuffer.position();
                    binBuffer.position(offset);
                    binBuffer.get(imgData);
                    binBuffer.position(oldPos);

                    Bitmap bitmap = BitmapFactory.decodeByteArray(imgData, 0, length);
                    if (bitmap != null) {
                        mesh.setModelTexture(Texture.createFromBitmap(render, bitmap, Texture.WrapMode.REPEAT, Texture.ColorFormat.SRGB));
                        textureFound = true;
                    }
                }
            }

            // 3. Fallback to baseColorFactor if no texture was found
            if (!textureFound) {
                Bitmap colorBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
                int r = (int) (baseColorFactor[0] * 255);
                int g = (int) (baseColorFactor[1] * 255);
                int b = (int) (baseColorFactor[2] * 255);
                int a = (int) (baseColorFactor[3] * 255);
                colorBitmap.setPixel(0, 0, android.graphics.Color.argb(a, r, g, b));
                mesh.setModelTexture(Texture.createFromBitmap(render, colorBitmap, Texture.WrapMode.CLAMP_TO_EDGE, Texture.ColorFormat.SRGB));
            }

        } catch (Exception e) {
            Log.e(TAG, "Could not extract color or texture from glTF", e);
        }

        return mesh;
    }

    private static FloatBuffer extractFloatBuffer(JSONObject json, ByteBuffer binBuffer, int accessorIdx, int expectedComponents) throws JSONException {
        JSONObject accessor = json.getJSONArray("accessors").getJSONObject(accessorIdx);
        int bufferViewIdx = accessor.getInt("bufferView");
        int count = accessor.getInt("count");
        int byteOffset = accessor.optInt("byteOffset", 0);

        JSONObject bufferView = json.getJSONArray("bufferViews").getJSONObject(bufferViewIdx);
        int bvByteOffset = bufferView.optInt("byteOffset", 0);
        int byteStride = bufferView.optInt("byteStride", 0);

        ByteBuffer result = ByteBuffer.allocateDirect(count * expectedComponents * 4).order(ByteOrder.nativeOrder());
        
        int elementSize = expectedComponents * 4;
        int stride = (byteStride == 0) ? elementSize : byteStride;

        for (int i = 0; i < count; i++) {
            binBuffer.position(bvByteOffset + byteOffset + i * stride);
            for (int j = 0; j < expectedComponents; j++) {
                result.putFloat(binBuffer.getFloat());
            }
        }
        result.rewind();
        return result.asFloatBuffer();
    }

    private static IntBuffer extractIntBuffer(JSONObject json, ByteBuffer binBuffer, int accessorIdx) throws JSONException {
        JSONObject accessor = json.getJSONArray("accessors").getJSONObject(accessorIdx);
        int bufferViewIdx = accessor.getInt("bufferView");
        int count = accessor.getInt("count");
        int byteOffset = accessor.optInt("byteOffset", 0);
        int componentType = accessor.getInt("componentType");

        JSONObject bufferView = json.getJSONArray("bufferViews").getJSONObject(bufferViewIdx);
        int bvByteOffset = bufferView.optInt("byteOffset", 0);
        int byteStride = bufferView.optInt("byteStride", 0);

        IntBuffer result = ByteBuffer.allocateDirect(count * 4).order(ByteOrder.nativeOrder()).asIntBuffer();
        
        int componentSize;
        if (componentType == 5123) componentSize = 2; // UNSIGNED_SHORT
        else if (componentType == 5125) componentSize = 4; // UNSIGNED_INT
        else if (componentType == 5121) componentSize = 1; // UNSIGNED_BYTE
        else componentSize = 4;

        int stride = (byteStride == 0) ? componentSize : byteStride;

        for (int i = 0; i < count; i++) {
            binBuffer.position(bvByteOffset + byteOffset + i * stride);
            if (componentType == 5123) { // UNSIGNED_SHORT
                result.put(binBuffer.getShort() & 0xFFFF);
            } else if (componentType == 5125) { // UNSIGNED_INT
                result.put(binBuffer.getInt());
            } else if (componentType == 5121) { // UNSIGNED_BYTE
                result.put(binBuffer.get() & 0xFF);
            }
        }
        result.rewind();
        return result;
    }

    private static byte[] readAllBytes(InputStream inputStream) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(Math.max(1024, inputStream.available()));
        byte[] temp = new byte[4096];
        int read;
        while ((read = inputStream.read(temp)) != -1) {
            if (buffer.remaining() < read) {
                ByteBuffer newBuffer = ByteBuffer.allocate(buffer.capacity() * 2 + read);
                buffer.flip();
                newBuffer.put(buffer);
                buffer = newBuffer;
            }
            buffer.put(temp, 0, read);
        }
        byte[] result = new byte[buffer.position()];
        buffer.flip();
        buffer.get(result);
        return result;
    }


    public static Mesh createFromFile(SampleRender render, String filePath) throws IOException {
        try (InputStream inputStream = new FileInputStream(filePath)) {
            if (filePath.toLowerCase().endsWith(".obj")) {
                return createFromObjStream(render, inputStream);
            } else if (filePath.toLowerCase().endsWith(".glb")) {
                return createFromGlbStream(render, inputStream);
            } else {
                throw new IllegalArgumentException("Unsupported file extension: " + filePath);
            }
        }
    }


    @Override
    public void close() {
        if (vertexArrayId[0] != 0) {
            GLES30.glDeleteVertexArrays(1, vertexArrayId, 0);
            GLError.maybeLogGLError(
                    Log.WARN, TAG, "Failed to free vertex array object", "glDeleteVertexArrays");
        }
    }

    /**
     * Draws the mesh. Don't call this directly unless you are doing low level OpenGL code; instead,
     * prefer {@link SampleRender#draw}.
     */
    public void lowLevelDraw() {
        if (vertexArrayId[0] == 0) {
            throw new IllegalStateException("Tried to draw a freed Mesh");
        }

        GLES30.glBindVertexArray(vertexArrayId[0]);
        GLError.maybeThrowGLException("Failed to bind vertex array object", "glBindVertexArray");

        if (indexBuffer == null || indexBuffer.getSize() == 0) {
            // Sin índices - usar glDrawArrays
            int vertexCount = vertexBuffers[0].getNumberOfVertices();
            if (vertexCount > 0) {
                GLES30.glDrawArrays(primitiveMode.glesEnum, 0, vertexCount);
                // Omitimos throw aquí para no interrumpir el frame si hay un error menor
            }
        } else {
            // Con índices - usar glDrawElements
            // El ELEMENT_ARRAY_BUFFER ya está vinculado al VAO desde el constructor.
            int indexCount = indexBuffer.getSize();
            if (indexCount > 0) {
                GLES30.glDrawElements(primitiveMode.glesEnum, indexCount, GLES30.GL_UNSIGNED_INT, 0);
            }
        }

        // Unbind para evitar contaminar el estado de otros objetos
        GLES30.glBindVertexArray(0);

        // ── Limpiar errores del draw para no contaminar el estado del framebuffer ──
        int err;
        while ((err = GLES30.glGetError()) != GLES30.GL_NO_ERROR) {
            Log.w(TAG, "GL error after draw (ignored): " + err);
        }
        // ────────────────────────────────────────────────────────────────────────────
    }

    public void debugPrintInfo() {
        Log.d(TAG, "Mesh Info:");
        Log.d(TAG, "  - Primitive Mode: " + primitiveMode);
        Log.d(TAG, "  - Has IndexBuffer: " + (indexBuffer != null));
        if (indexBuffer != null) {
            Log.d(TAG, "  - IndexBuffer size: " + indexBuffer.getSize());
        }
        Log.d(TAG, "  - VertexBuffers count: " + vertexBuffers.length);
        for (int i = 0; i < vertexBuffers.length; i++) {
            Log.d(TAG, "  - VertexBuffer[" + i + "] vertices: " + vertexBuffers[i].getNumberOfVertices());
        }
    }

    public Texture getModelTexture() {
        return modelTexture;
    }

    public void setModelTexture(Texture texture) {
        this.modelTexture = texture;
    }
}
