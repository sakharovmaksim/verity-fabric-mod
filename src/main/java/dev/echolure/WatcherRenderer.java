package dev.echolure;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

final class WatcherRenderer extends HumanoidMobRenderer<Actors.Watcher, HumanoidRenderState, WatcherRenderer.Model> {
    private static final Identifier TEXTURE =
        Identifier.fromNamespaceAndPath("echo_lure", "textures/entity/watcher.png");

    WatcherRenderer(EntityRendererProvider.Context context) {
        super(context, new Model(Model.mesh().bakeRoot()), 0.42f);
    }

    @Override
    public HumanoidRenderState createRenderState() {
        return new HumanoidRenderState();
    }

    @Override
    public Identifier getTextureLocation(HumanoidRenderState state) {
        return TEXTURE;
    }

    static final class Model extends HumanoidModel<HumanoidRenderState> {
        private final ModelPart jaw;

        Model(ModelPart root) {
            super(root);
            jaw = head.getChild("jaw");
        }

        private static LayerDefinition mesh() {
            var mesh = new MeshDefinition();
            var root = mesh.getRoot();
            var head = root.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-3.5f, -8, -3.5f, 7, 6, 6)
                .texOffs(0, 14).addBox(-3, -2, -2, 6, 5, 4)
                .texOffs(25, 0).addBox(-3, -9, -2.5f, 5, 1, 4),
                PartPose.offset(0, -1, -2));
            head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
            head.addOrReplaceChild("jaw", CubeListBuilder.create()
                .texOffs(25, 6).addBox(-2.5f, 0, -5, 5, 2, 6),
                PartPose.offsetAndRotation(0.5f, 2, 1.5f, -0.38f, 0, 0.08f));

            root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(0, 32).addBox(-4.5f, 0, -3, 9, 7, 6)
                .texOffs(31, 32).addBox(-2.5f, 6, -1.5f, 5, 6, 4)
                .texOffs(51, 32).addBox(-3, 1, 2.5f, 6, 4, 2),
                PartPose.ZERO);

            root.addOrReplaceChild("right_arm", CubeListBuilder.create()
                .texOffs(0, 50).addBox(-2, -1, -1.5f, 3, 8, 3)
                .texOffs(13, 50).addBox(-1.5f, 6.5f, -1.5f, 2.5f, 10, 2.5f)
                .texOffs(25, 50).addBox(-1.5f, 15.5f, -2, 2.5f, 4, 3),
                PartPose.offset(-5, 2, 0));
            root.addOrReplaceChild("left_arm", CubeListBuilder.create()
                .texOffs(39, 50).addBox(-1, -1, -1.5f, 3, 8, 3)
                .texOffs(53, 50).addBox(-1, 6.5f, -1.5f, 2.5f, 10, 2.5f)
                .texOffs(65, 50).addBox(-1, 15.5f, -2, 2.5f, 4, 3),
                PartPose.offset(5, 2, 0));
            root.addOrReplaceChild("right_leg", CubeListBuilder.create()
                .texOffs(0, 69).addBox(-1.5f, 0, -1.5f, 3, 6, 3)
                .texOffs(13, 69).addBox(-1.25f, 5, -1, 2.5f, 6, 2.5f)
                .texOffs(25, 69).addBox(-1.25f, 10, -2.5f, 2.5f, 2, 4),
                PartPose.offset(-1.8f, 12, 0.5f));
            root.addOrReplaceChild("left_leg", CubeListBuilder.create()
                .texOffs(39, 69).addBox(-1.5f, 0, -1.5f, 3, 6, 3)
                .texOffs(53, 69).addBox(-1.25f, 5, -1, 2.5f, 6, 2.5f)
                .texOffs(65, 69).addBox(-1.25f, 10, -2.5f, 2.5f, 2, 4),
                PartPose.offset(1.8f, 12, 0.5f));
            return LayerDefinition.create(mesh, 96, 96);
        }

        @Override
        public void setupAnim(HumanoidRenderState state) {
            super.setupAnim(state);
            body.xRot += 0.20f;
            head.xRot += 0.1f;
            head.zRot += Mth.sin(state.ageInTicks * 0.06f) * 0.09f;
            rightArm.zRot += 0.09f;
            leftArm.zRot -= 0.15f;
            leftArm.xRot -= 0.21f;
            jaw.xRot -= 0.16f + Mth.sin(state.ageInTicks * 0.13f) * 0.12f
                + Mth.sin(state.swingAnimation * Mth.PI) * 0.44f;
        }
    }
}
