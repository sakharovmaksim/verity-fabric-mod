package dev.echolure;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

final class BoxRenderer extends MobRenderer<ArrivalBox, BoxRenderer.State, BoxRenderer.Model> {
    private static final Identifier TEXTURE =
        Identifier.fromNamespaceAndPath("echo_lure", "textures/entity/arrival_box.png");

    BoxRenderer(EntityRendererProvider.Context context) {
        super(context, new Model(), 0.45f);
    }

    @Override public State createRenderState() { return new State(); }
    @Override public Identifier getTextureLocation(State state) { return TEXTURE; }

    @Override
    public void extractRenderState(ArrivalBox box, State state, float partialTick) {
        super.extractRenderState(box, state, partialTick);
        state.opening = box.opening(partialTick);
    }

    static final class State extends LivingEntityRenderState {
        private float opening;
    }

    static final class Model extends EntityModel<State> {
        private final ModelPart parcel;
        private final ModelPart front;
        private final ModelPart back;
        private final ModelPart left;
        private final ModelPart right;

        private Model() {
            super(mesh().bakeRoot());
            parcel = root().getChild("parcel");
            front = parcel.getChild("front");
            back = parcel.getChild("back");
            left = parcel.getChild("left");
            right = parcel.getChild("right");
        }

        private static LayerDefinition mesh() {
            var mesh = new MeshDefinition();
            var parcel = mesh.getRoot().addOrReplaceChild("parcel", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-6, -1, -6, 12, 1, 12)
                .texOffs(0, 16).addBox(-6, -9, -6, 12, 8, 1)
                .texOffs(0, 16).addBox(-6, -9, 5, 12, 8, 1)
                .texOffs(32, 0).addBox(-6, -9, -5, 1, 8, 10)
                .texOffs(32, 0).addBox(5, -9, -5, 1, 8, 10), PartPose.offset(0, 24, 0));
            parcel.addOrReplaceChild("front", CubeListBuilder.create()
                .texOffs(0, 28).addBox(-6, -0.5f, 0, 12, 0.5f, 6), PartPose.offset(0, -9, -6));
            parcel.addOrReplaceChild("back", CubeListBuilder.create()
                .texOffs(0, 28).addBox(-6, -0.5f, -6, 12, 0.5f, 6), PartPose.offset(0, -9, 6));
            parcel.addOrReplaceChild("left", CubeListBuilder.create()
                .texOffs(0, 38).addBox(0, -0.4f, -6, 6, 0.4f, 12), PartPose.offset(-6, -9.3f, 0));
            parcel.addOrReplaceChild("right", CubeListBuilder.create()
                .texOffs(0, 38).addBox(-6, -0.4f, -6, 6, 0.4f, 12), PartPose.offset(6, -9.3f, 0));
            return LayerDefinition.create(mesh, 64, 64);
        }

        @Override
        public void setupAnim(State state) {
            super.setupAnim(state);
            float opening = state.opening * state.opening * (3 - 2 * state.opening);
            float outerFlaps = opening * 2.15f;
            float innerFlaps = Math.max(0, opening - 0.15f) * 2.3f;
            front.xRot = innerFlaps;
            back.xRot = -innerFlaps;
            left.zRot = -outerFlaps;
            right.zRot = outerFlaps;
            float shake = state.opening < 1 ? Mth.sin(state.ageInTicks * 0.9f) * 0.025f : 0;
            parcel.zRot = shake;
            parcel.xRot = shake * 0.5f;
        }
    }
}
