#include <jni.h>

#include <STEPControl_Reader.hxx>
#include <IFSelect_ReturnStatus.hxx>
#include <TopoDS_Shape.hxx>
#include <TopoDS_Face.hxx>
#include <TopoDS.hxx>
#include <TopExp_Explorer.hxx>
#include <TopAbs_ShapeEnum.hxx>
#include <TopAbs_Orientation.hxx>
#include <BRepMesh_IncrementalMesh.hxx>
#include <BRep_Tool.hxx>
#include <Poly_Triangulation.hxx>
#include <Poly_Triangle.hxx>
#include <TopLoc_Location.hxx>
#include <gp_Trsf.hxx>
#include <gp_Pnt.hxx>
#include <Standard_Failure.hxx>

#include <algorithm>
#include <cstdint>
#include <cstring>
#include <limits>
#include <string>
#include <vector>

namespace {

constexpr std::uint32_t kMagic = 0x4F434354; // "OCCT"
constexpr std::uint32_t kVersion = 1;

void throwRuntime(JNIEnv* env, const std::string& message) {
    jclass cls = env->FindClass("java/lang/RuntimeException");
    if (cls != nullptr) {
        env->ThrowNew(cls, message.c_str());
    }
}

void appendU32(std::vector<std::uint8_t>& out, std::uint32_t value) {
    out.push_back(static_cast<std::uint8_t>(value & 0xFFu));
    out.push_back(static_cast<std::uint8_t>((value >> 8) & 0xFFu));
    out.push_back(static_cast<std::uint8_t>((value >> 16) & 0xFFu));
    out.push_back(static_cast<std::uint8_t>((value >> 24) & 0xFFu));
}

void appendFloat(std::vector<std::uint8_t>& out, float value) {
    std::uint32_t bits = 0;
    static_assert(sizeof(bits) == sizeof(value), "float must be 32-bit");
    std::memcpy(&bits, &value, sizeof(value));
    appendU32(out, bits);
}

TopoDS_Shape readStep(const char* path) {
    STEPControl_Reader reader;
    IFSelect_ReturnStatus status = IFSelect_RetFail;
    try {
        status = reader.ReadFile(path);
    } catch (const Standard_Failure&) {
        return TopoDS_Shape();
    }
    if (status != IFSelect_RetDone || reader.NbRootsForTransfer() <= 0) {
        return TopoDS_Shape();
    }
    reader.TransferRoots();
    if (reader.NbShapes() <= 0) {
        return TopoDS_Shape();
    }
    return reader.OneShape();
}

bool triangulateShape(
    const TopoDS_Shape& shape,
    std::vector<float>& positions,
    std::vector<std::uint32_t>& indices
) {
    if (shape.IsNull()) {
        return false;
    }

    // Viewer-oriented tessellation. Relative deflection adapts to model size,
    // while ~20 degrees keeps curved CAD surfaces reasonably smooth on mobile.
    try {
        BRepMesh_IncrementalMesh mesher(
            shape,
            0.0015,
            Standard_True,
            0.35,
            Standard_True
        );
        mesher.Perform();
    } catch (const Standard_Failure&) {
        return false;
    }

    for (TopExp_Explorer explorer(shape, TopAbs_FACE); explorer.More(); explorer.Next()) {
        const TopoDS_Face face = TopoDS::Face(explorer.Current());
        TopLoc_Location location;
        const Handle(Poly_Triangulation) tri = BRep_Tool::Triangulation(face, location);
        if (tri.IsNull() || tri->NbNodes() <= 0 || tri->NbTriangles() <= 0) {
            continue;
        }

        const std::size_t vertexBase = positions.size() / 3u;
        if (vertexBase + static_cast<std::size_t>(tri->NbNodes()) >
            static_cast<std::size_t>(std::numeric_limits<std::uint32_t>::max())) {
            return false;
        }

        const gp_Trsf transform = location.Transformation();
        positions.reserve(positions.size() + static_cast<std::size_t>(tri->NbNodes()) * 3u);
        for (Standard_Integer nodeIndex = 1; nodeIndex <= tri->NbNodes(); ++nodeIndex) {
            gp_Pnt point = tri->Node(nodeIndex);
            point.Transform(transform);
            positions.push_back(static_cast<float>(point.X()));
            positions.push_back(static_cast<float>(point.Y()));
            positions.push_back(static_cast<float>(point.Z()));
        }

        indices.reserve(indices.size() + static_cast<std::size_t>(tri->NbTriangles()) * 3u);
        const bool reversed = face.Orientation() == TopAbs_REVERSED;
        for (Standard_Integer triangleIndex = 1; triangleIndex <= tri->NbTriangles(); ++triangleIndex) {
            Standard_Integer n1 = 0;
            Standard_Integer n2 = 0;
            Standard_Integer n3 = 0;
            tri->Triangle(triangleIndex).Get(n1, n2, n3);
            if (reversed) {
                std::swap(n2, n3);
            }
            indices.push_back(static_cast<std::uint32_t>(vertexBase + static_cast<std::size_t>(n1 - 1)));
            indices.push_back(static_cast<std::uint32_t>(vertexBase + static_cast<std::size_t>(n2 - 1)));
            indices.push_back(static_cast<std::uint32_t>(vertexBase + static_cast<std::size_t>(n3 - 1)));
        }
    }

    return !positions.empty() && indices.size() >= 3u;
}

} // namespace

extern "C"
JNIEXPORT jbyteArray JNICALL
Java_com_edgar_viewer3d_OcctStepImporter_nativeLoadStep(
    JNIEnv* env,
    jobject,
    jstring path
) {
    if (path == nullptr) {
        throwRuntime(env, "STEP path is missing.");
        return nullptr;
    }

    const char* pathChars = env->GetStringUTFChars(path, nullptr);
    if (pathChars == nullptr) {
        throwRuntime(env, "Could not read STEP path.");
        return nullptr;
    }

    TopoDS_Shape shape;
    try {
        shape = readStep(pathChars);
    } catch (const Standard_Failure& failure) {
        env->ReleaseStringUTFChars(path, pathChars);
        throwRuntime(env, std::string("OCCT STEP import failed: ") + failure.GetMessageString());
        return nullptr;
    }
    env->ReleaseStringUTFChars(path, pathChars);

    if (shape.IsNull()) {
        throwRuntime(env, "OCCT could not translate this STEP/STP file into a CAD shape.");
        return nullptr;
    }

    std::vector<float> positions;
    std::vector<std::uint32_t> indices;
    if (!triangulateShape(shape, positions, indices)) {
        throwRuntime(env, "OCCT loaded the STEP file but could not create display triangles.");
        return nullptr;
    }

    const std::uint64_t vertexCount = positions.size() / 3u;
    const std::uint64_t indexCount = indices.size();
    if (vertexCount > std::numeric_limits<std::uint32_t>::max() ||
        indexCount > std::numeric_limits<std::uint32_t>::max()) {
        throwRuntime(env, "STEP model is too large for the Android mesh bridge.");
        return nullptr;
    }

    const std::uint64_t payloadSize =
        16u +
        static_cast<std::uint64_t>(positions.size()) * sizeof(float) +
        static_cast<std::uint64_t>(indices.size()) * sizeof(std::uint32_t);

    if (payloadSize > static_cast<std::uint64_t>(std::numeric_limits<jsize>::max())) {
        throwRuntime(env, "STEP triangulation exceeds Android byte-array limits.");
        return nullptr;
    }

    std::vector<std::uint8_t> packed;
    packed.reserve(static_cast<std::size_t>(payloadSize));
    appendU32(packed, kMagic);
    appendU32(packed, kVersion);
    appendU32(packed, static_cast<std::uint32_t>(vertexCount));
    appendU32(packed, static_cast<std::uint32_t>(indexCount));
    for (float value : positions) {
        appendFloat(packed, value);
    }
    for (std::uint32_t value : indices) {
        appendU32(packed, value);
    }

    jbyteArray result = env->NewByteArray(static_cast<jsize>(packed.size()));
    if (result == nullptr) {
        throwRuntime(env, "Android could not allocate memory for the STEP mesh.");
        return nullptr;
    }
    env->SetByteArrayRegion(
        result,
        0,
        static_cast<jsize>(packed.size()),
        reinterpret_cast<const jbyte*>(packed.data())
    );
    return result;
}
