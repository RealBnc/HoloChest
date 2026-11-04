package com.holochest.hologram;

import com.holochest.render.HoloHudRenderer;
import com.holochest.sound.HoloSoundHelper;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.enums.ChestType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.InputUtil;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Vector2f;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class HoloChestManager {

    private static final HoloChestManager INSTANCE = new HoloChestManager();

    private final Map<BlockPos, HoloSession> activeSessions = new ConcurrentHashMap<>();

    private HoloSession hoveredSession = null;
    private int hoveredSlot = -1;
    private int lastHoveredSlot = -1;
    private BlockPos lastHoveredSessionPos = null;

    private boolean isAimingAtPanel = false;

    private final List<FlyingItem> flyingItems = new CopyOnWriteArrayList<>();

    private HoloChestManager() {}

    public static HoloChestManager getInstance() {
        return INSTANCE;
    }

    public boolean hasActiveSessions() {
        return !activeSessions.isEmpty();
    }

    public boolean hasAnyNonClosingSession() {
        for (HoloSession session : activeSessions.values()) {
            if (!session.isClosing()) {
                return true;
            }
        }
        return false;
    }

    public Collection<HoloSession> getActiveSessions() {
        return activeSessions.values();
    }

    public HoloSession getSession(BlockPos pos) {
        return activeSessions.get(pos);
    }

    public List<FlyingItem> getFlyingItems() {
        return flyingItems;
    }

    public HoloSession getHoveredSession() {
        return hoveredSession;
    }

    public int getHoveredSlot() {
        return hoveredSlot;
    }

    public boolean isAimingAtAnyPanel() {
        return isAimingAtPanel;
    }

    public void setAimingAtPanel(boolean aiming) {
        this.isAimingAtPanel = aiming;
    }

    public void setHoverState(HoloSession session, int slot) {
        this.hoveredSession = session;
        this.hoveredSlot = slot;

        BlockPos currentPos = (session != null) ? session.getPos() : null;
        boolean posChanged = (currentPos != null && !currentPos.equals(lastHoveredSessionPos)) || (currentPos == null && lastHoveredSessionPos != null);

        if (slot != this.lastHoveredSlot || posChanged) {
            if (slot != -1) {
                HoloSoundHelper.playSlotHover();
            }
            this.lastHoveredSlot = slot;
            this.lastHoveredSessionPos = currentPos;
        }
    }

    public boolean isContainerAjar(BlockPos pos) {
        if (pos == null || activeSessions.isEmpty()) return false;
        for (HoloSession session : activeSessions.values()) {
            if (session.isMatchingPos(pos)) {
                return true;
            }
        }
        return false;
    }

    public float getContainerOpenProgress(BlockPos pos) {
        if (pos == null) return 0f;
        for (HoloSession session : activeSessions.values()) {
            if (session.isMatchingPos(pos)) {
                return session.getOpenProgress();
            }
        }
        return 0f;
    }

    public void toggle() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) return;

        BlockHitResult hit = raycastContainer(player, client);
        if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = hit.getBlockPos();
            double distSq = player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            if (distSq <= 40.0 * 40.0) {
                BlockState state = client.world.getBlockState(pos);

                if (isContainerBlock(state)) {
                BlockPos canonPos = getCanonicalChestPos(client.world, pos, state);
                HoloSession current = activeSessions.get(canonPos);
                if (current != null && !current.isClosing()) {
                    float curProg = current.getOpenProgress();
                    startClosingSession(current);
                    HoloSoundHelper.playHoloClose(curProg);
                } else {
                    openContainer(canonPos, state);
                }
                    return;
                }
            }
        }

        if (!activeSessions.isEmpty()) {
            startClosingAll();
        }
    }

    public static boolean isContainerBlock(BlockState state) {
        Block b = state.getBlock();
        return b instanceof AbstractChestBlock
                || b instanceof BarrelBlock
                || b instanceof ShulkerBoxBlock
                || b instanceof HopperBlock
                || b instanceof AbstractFurnaceBlock
                || b instanceof DispenserBlock
                || b instanceof DropperBlock
                || b instanceof CrafterBlock
                || b instanceof BrewingStandBlock
                || b instanceof ChiseledBookshelfBlock
                || b instanceof EnderChestBlock;
    }

    private BlockHitResult raycastContainer(ClientPlayerEntity player, MinecraftClient client) {
        Vec3d eyePos = player.getEyePos();
        Vec3d rot = player.getRotationVec(1.0f);
        Vec3d reachEnd = eyePos.add(rot.multiply(40.0));

        return client.world.raycast(new RaycastContext(
                eyePos,
                reachEnd,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                player
        ));
    }

    public static BlockPos getDoubleChestPartner(net.minecraft.world.World world, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof ChestBlock && state.contains(ChestBlock.CHEST_TYPE) && state.contains(ChestBlock.FACING)) {
            ChestType type = state.get(ChestBlock.CHEST_TYPE);
            if (type != ChestType.SINGLE) {
                Direction facing = state.get(ChestBlock.FACING);
                Direction partnerDir = (type == ChestType.LEFT) ? facing.rotateYClockwise() : facing.rotateYCounterclockwise();
                BlockPos partnerPos = pos.offset(partnerDir);
                BlockState partnerState = world.getBlockState(partnerPos);
                if (partnerState.getBlock() == state.getBlock() && partnerState.contains(ChestBlock.CHEST_TYPE)) {
                    ChestType partnerType = partnerState.get(ChestBlock.CHEST_TYPE);
                    if (partnerType != ChestType.SINGLE && partnerType != type && partnerState.get(ChestBlock.FACING) == facing) {
                        return partnerPos;
                    }
                }
            }
        }
        return null;
    }

    public static BlockPos getCanonicalChestPos(net.minecraft.world.World world, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof ChestBlock && state.contains(ChestBlock.CHEST_TYPE) && state.contains(ChestBlock.FACING)) {
            ChestType type = state.get(ChestBlock.CHEST_TYPE);
            if (type == ChestType.RIGHT) {
                Direction facing = state.get(ChestBlock.FACING);
                Direction partnerDir = facing.rotateYCounterclockwise();
                BlockPos partnerPos = pos.offset(partnerDir);
                BlockState partnerState = world.getBlockState(partnerPos);
                if (partnerState.getBlock() == state.getBlock() && partnerState.contains(ChestBlock.CHEST_TYPE)) {
                    if (partnerState.get(ChestBlock.CHEST_TYPE) == ChestType.LEFT && partnerState.get(ChestBlock.FACING) == facing) {
                        return partnerPos;
                    }
                }
            }
        }
        return pos;
    }

    public static SoundEvent getContainerOpenSound(BlockState state) {
        Block b = state.getBlock();
        if (b instanceof ShulkerBoxBlock) return SoundEvents.BLOCK_SHULKER_BOX_OPEN;
        if (b instanceof BarrelBlock) return SoundEvents.BLOCK_BARREL_OPEN;
        if (b instanceof EnderChestBlock) return SoundEvents.BLOCK_ENDER_CHEST_OPEN;
        if (b instanceof HopperBlock || b instanceof DispenserBlock || b instanceof DropperBlock || b instanceof CrafterBlock) {
            return SoundEvents.BLOCK_IRON_TRAPDOOR_OPEN;
        }
        if (b instanceof AbstractFurnaceBlock) return SoundEvents.BLOCK_FURNACE_FIRE_CRACKLE;
        return SoundEvents.BLOCK_CHEST_OPEN;
    }

    public static SoundEvent getContainerCloseSound(BlockState state) {
        Block b = state.getBlock();
        if (b instanceof ShulkerBoxBlock) return SoundEvents.BLOCK_SHULKER_BOX_CLOSE;
        if (b instanceof BarrelBlock) return SoundEvents.BLOCK_BARREL_CLOSE;
        if (b instanceof EnderChestBlock) return SoundEvents.BLOCK_ENDER_CHEST_CLOSE;
        if (b instanceof HopperBlock || b instanceof DispenserBlock || b instanceof DropperBlock || b instanceof CrafterBlock) {
            return SoundEvents.BLOCK_IRON_TRAPDOOR_CLOSE;
        }
        if (b instanceof AbstractFurnaceBlock) return SoundEvents.BLOCK_IRON_TRAPDOOR_CLOSE;
        return SoundEvents.BLOCK_CHEST_CLOSE;
    }

    public static void playContainerSound(net.minecraft.world.World world, BlockPos pos, SoundEvent sound) {

    }

    public static int getContainerTitleColor(BlockState state) {
        Block b = state.getBlock();
        if (b instanceof ShulkerBoxBlock shulker) {
            DyeColor dye = shulker.getColor();
            if (dye != null) {
                return switch (dye) {
                    case WHITE -> 0xF9FFFE;
                    case ORANGE -> 0xF9801D;
                    case MAGENTA -> 0xC74EBD;
                    case LIGHT_BLUE -> 0x3AB3DA;
                    case YELLOW -> 0xFED83D;
                    case LIME -> 0x80C71F;
                    case PINK -> 0xF38BAA;
                    case GRAY -> 0x474F52;
                    case LIGHT_GRAY -> 0x9D9D97;
                    case CYAN -> 0x169C9C;
                    case PURPLE -> 0x9933FF;
                    case BLUE -> 0x3C44AA;
                    case BROWN -> 0x835432;
                    case GREEN -> 0x5E7C16;
                    case RED -> 0xEF4444;
                    case BLACK -> 0x303030;
                };
            }
            return 0xBA68C8;
        }
        if (b instanceof EnderChestBlock) {
            return 0x20E3B2;
        }
        if (b instanceof BarrelBlock) {
            return 0xD29034;
        }
        if (b instanceof ChestBlock) {
            return 0xF0A852;
        }
        if (b instanceof HopperBlock) {
            return 0xC0C0D4;
        }
        if (b instanceof AbstractFurnaceBlock) {
            return 0xFF7700;
        }
        if (b instanceof BrewingStandBlock) {
            return 0xF59E0B;
        }
        if (b instanceof DispenserBlock || b instanceof DropperBlock || b instanceof CrafterBlock) {
            return 0x9CA3AF;
        }
        return 0x00E5FF;
    }

    public void openContainer(BlockPos pos, BlockState state) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;

        BlockPos canonPos = getCanonicalChestPos(client.world, pos, state);

        for (HoloSession s : activeSessions.values()) {
            startClosingSession(s);
        }

        BlockState canonState = client.world.getBlockState(canonPos);
        BlockPos partnerPos = getDoubleChestPartner(client.world, canonPos, canonState);

        Text title = canonState.getBlock().getName();
        int titleColor = getContainerTitleColor(canonState);
        SoundEvent openSound = getContainerOpenSound(canonState);
        SoundEvent closeSound = getContainerCloseSound(canonState);

        int initialSize = 27;
        Block b = canonState.getBlock();
        if (b instanceof ChestBlock && canonState.contains(ChestBlock.CHEST_TYPE)) {
            ChestType type = canonState.get(ChestBlock.CHEST_TYPE);
            initialSize = (type != ChestType.SINGLE) ? 54 : 27;
        } else if (b instanceof HopperBlock) {
            initialSize = 5;
        } else if (b instanceof AbstractFurnaceBlock) {
            initialSize = 3;
        } else if (b instanceof DispenserBlock || b instanceof DropperBlock || b instanceof CrafterBlock) {
            initialSize = 9;
        } else if (b instanceof BrewingStandBlock) {
            initialSize = 5;
        } else if (b instanceof ChiseledBookshelfBlock) {
            initialSize = 6;
        }

        HoloSession session = new HoloSession(canonPos, partnerPos, title, titleColor, initialSize, openSound, closeSound);
        if (b instanceof AbstractFurnaceBlock) {
            session.setFurnace(true);
        }
        activeSessions.put(canonPos, session);

        if (b instanceof EnderChestBlock && client.player != null) {
            Inventory clientEnder = client.player.getEnderChestInventory();
            for (int i = 0; i < clientEnder.size() && i < session.getCachedStacks().size(); i++) {
                session.setStack(i, clientEnder.getStack(i).copy());
            }
        }

        if (b instanceof BarrelBlock && state.contains(BarrelBlock.OPEN)) {
            client.world.setBlockState(canonPos, state.with(BarrelBlock.OPEN, true), 3);
            MinecraftServer server = client.getServer();
            if (server != null) {
                server.execute(() -> {
                    ServerWorld sWorld = server.getWorld(client.world.getRegistryKey());
                    if (sWorld != null) {
                        BlockState sState = sWorld.getBlockState(canonPos);
                        if (sState.getBlock() instanceof BarrelBlock && sState.contains(BarrelBlock.OPEN)) {
                            sWorld.setBlockState(canonPos, sState.with(BarrelBlock.OPEN, true), 3);
                        }
                    }
                });
            }
        }

        syncSessionFromServer(session);
        HoloSoundHelper.playHoloOpen(0f);
    }

    

    

    public void recalculateSessionLayout() {}

    public void syncSessionFromServer(HoloSession session) {
        MinecraftClient client = MinecraftClient.getInstance();
        MinecraftServer server = client.getServer();
        if (server != null && client.world != null && client.player != null) {
            BlockPos targetPos = session.getPos();
            java.util.UUID playerUuid = client.player.getUuid();
            server.execute(() -> {
                ServerWorld sWorld = server.getWorld(client.world.getRegistryKey());
                if (sWorld == null) return;
                ServerPlayerEntity sPlayer = server.getPlayerManager().getPlayer(playerUuid);
                Inventory inv = getContainerInventoryDirect(sWorld, targetPos, sPlayer);
                if (inv != null) {
                    int size = inv.size();
                    DefaultedList<ItemStack> copy = DefaultedList.ofSize(size, ItemStack.EMPTY);
                    for (int i = 0; i < size; i++) {
                        copy.set(i, inv.getStack(i).copy());
                    }
                    float litProg = 0f;
                    float cookProg = 0f;
                    BlockEntity be = sWorld.getBlockEntity(targetPos);
                    if (be instanceof net.minecraft.block.entity.AbstractFurnaceBlockEntity furnace) {
                        try {
                            net.minecraft.screen.PropertyDelegate pd = ((com.holochest.mixin.AbstractFurnaceBlockEntityAccessor) furnace).getPropertyDelegate();
                            if (pd != null) {
                                int litRemaining = pd.get(0);
                                int litTotal = pd.get(1);
                                int cookSpent = pd.get(2);
                                int cookTotal = pd.get(3);
                                litProg = (litTotal > 0) ? Math.clamp((float) litRemaining / (float) litTotal, 0f, 1f) : 0f;
                                cookProg = (cookTotal > 0) ? Math.clamp((float) cookSpent / (float) cookTotal, 0f, 1f) : 0f;
                            }
                        } catch (Throwable ignored) {}
                    }
                    final float finalLitProg = litProg;
                    final float finalCookProg = cookProg;
                    client.execute(() -> {
                        if (activeSessions.containsKey(targetPos)) {
                            session.updateSize(size);
                            for (int i = 0; i < size && i < session.getCachedStacks().size(); i++) {
                                session.setStack(i, copy.get(i));
                            }
                            session.setFurnaceLitProgress(finalLitProg);
                            session.setFurnaceCookProgress(finalCookProg);
                        }
                    });
                }
            });
        }
    }

    public static Inventory getContainerInventoryDirect(ServerWorld sWorld, BlockPos pos, ServerPlayerEntity sPlayer) {
        BlockState state = sWorld.getBlockState(pos);
        Block block = state.getBlock();
        if (block instanceof ChestBlock chestBlock) {
            return ChestBlock.getInventory(chestBlock, state, sWorld, pos, true);
        }
        if (block instanceof EnderChestBlock && sPlayer != null) {
            return sPlayer.getEnderChestInventory();
        }
        BlockEntity be = sWorld.getBlockEntity(pos);
        if (be instanceof Inventory inv) {
            return inv;
        }
        return null;
    }


    public void startClosingAll() {
        MinecraftClient client = MinecraftClient.getInstance();
        float maxProg = 0f;
        boolean anyNotClosing = false;
        for (HoloSession session : activeSessions.values()) {
            if (!session.isClosing()) {
                maxProg = Math.max(maxProg, session.getOpenProgress());
                anyNotClosing = true;
            }
            startClosingSession(session);
        }
        if (anyNotClosing) {
            HoloSoundHelper.playHoloClose(maxProg > 0f ? maxProg : 1.0f);
        }
        
    }

    private BlockPos lastInteractedPos = null;
    private long lastInteractionTime = 0;

    public void recordBlockInteraction(BlockPos pos) {
        this.lastInteractedPos = pos;
        this.lastInteractionTime = System.currentTimeMillis();
    }

    public BlockPos getLastInteractedBlockPos() {
        return lastInteractedPos;
    }

    public void onVanillaContainerScreenOpened() {
        MinecraftClient client = MinecraftClient.getInstance();
        BlockPos targetPos = lastInteractedPos;
        long elapsed = System.currentTimeMillis() - lastInteractionTime;

        if (targetPos == null || elapsed > 3000) {
            if (client.crosshairTarget instanceof BlockHitResult bhr && bhr.getType() == HitResult.Type.BLOCK) {
                targetPos = bhr.getBlockPos();
            }
        }

        if (targetPos == null && client.world != null && client.player != null) {
            Vec3d playerPos = client.player.getEyePos();
            double closestDistSq = Double.MAX_VALUE;
            BlockPos closestPos = null;
            for (HoloSession session : activeSessions.values()) {
                double distSq = session.getPos().getSquaredDistance(playerPos);
                if (distSq < closestDistSq && distSq <= 36.0) {
                    closestDistSq = distSq;
                    closestPos = session.getPos();
                }
            }
            targetPos = closestPos;
        }

        if (targetPos == null) return;

        BlockPos toRemovePos = null;
        for (HoloSession session : activeSessions.values()) {
            if (session.isMatchingPos(targetPos)) {
                toRemovePos = session.getPos();
                break;
            }
        }

        if (toRemovePos != null) {
            HoloSession s = activeSessions.get(toRemovePos);
            if (s != null && !s.isClosing()) {
                float curProg = s.getOpenProgress();
                startClosingSession(s);
                HoloSoundHelper.playHoloClose(curProg);
            }
        }

        lastInteractedPos = null;
    }

    public void forceCloseAll() {
        flyingItems.clear();
        closeBarrelBlocks();
        activeSessions.clear();
        hoveredSession = null;
        hoveredSlot = -1;
        lastHoveredSlot = -1;
        lastHoveredSessionPos = null;
        isAimingAtPanel = false;
    }

    public void startClosingSession(HoloSession session) {
        if (session == null) return;
        if (!session.isClosing()) {
            session.startClosing();
        }
        closeBarrelBlock(session.getPos());
        if (session.getPartnerPos() != null) {
            closeBarrelBlock(session.getPartnerPos());
        }
    }

    public void closeBarrelBlock(BlockPos pos) {
        if (pos == null) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;
        BlockState state = client.world.getBlockState(pos);
        if (state.getBlock() instanceof BarrelBlock && state.contains(BarrelBlock.OPEN)) {
            client.world.setBlockState(pos, state.with(BarrelBlock.OPEN, false), 3);
        }
        MinecraftServer server = client.getServer();
        if (server != null) {
            server.execute(() -> {
                ServerWorld sWorld = server.getWorld(client.world.getRegistryKey());
                if (sWorld != null) {
                    BlockState sState = sWorld.getBlockState(pos);
                    if (sState.getBlock() instanceof BarrelBlock && sState.contains(BarrelBlock.OPEN)) {
                        sWorld.setBlockState(pos, sState.with(BarrelBlock.OPEN, false), 3);
                    }
                }
            });
        }
    }

    private void closeBarrelBlocks() {
        for (HoloSession session : activeSessions.values()) {
            closeBarrelBlock(session.getPos());
            if (session.getPartnerPos() != null) {
                closeBarrelBlock(session.getPartnerPos());
            }
        }
    }

    public static boolean isShiftHeld() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getWindow() == null) return false;
        return InputUtil.isKeyPressed(client.getWindow(), GLFW.GLFW_KEY_LEFT_SHIFT)
                || InputUtil.isKeyPressed(client.getWindow(), GLFW.GLFW_KEY_RIGHT_SHIFT)
                || (client.options != null && client.options.sneakKey.isPressed());
    }

    public static Vector2f getHotbarSlotScreenPos(int hotbarSlot, int sw, int sh) {
        if (hotbarSlot < 0 || hotbarSlot > 8) {
            return new Vector2f(sw * 0.65f, sh - 20f);
        }
        float hotbarStartX = (sw - 182) / 2f;
        float slotCenterX = hotbarStartX + 3f + (hotbarSlot * 20f) + 8f;
        float slotCenterY = sh - 11f;
        return new Vector2f(slotCenterX, slotCenterY);
    }

    public static int findTargetSlotForStack(PlayerInventory inv, ItemStack stack) {
        for (int i = 0; i < 9; i++) {
            ItemStack s = inv.getStack(i);
            if (!s.isEmpty() && ItemStack.areItemsAndComponentsEqual(s, stack) && s.getCount() < s.getMaxCount()) {
                return i;
            }
        }
        for (int i = 0; i < 9; i++) {
            if (inv.getStack(i).isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    public void handleLeftClick() {
        if (hoveredSession == null) return;

        if (hoveredSession.isSortButtonHovered()) {
            sortSession(hoveredSession);
            return;
        }

        if (hoveredSlot == -1) return;

        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null) return;

        if (hoveredSlot >= hoveredSession.getCachedSize()) return;
        ItemStack stack = hoveredSession.getStack(hoveredSlot);
        if (stack.isEmpty()) return;

        boolean shiftHeld = isShiftHeld();
        int countToTake = shiftHeld ? stack.getCount() : 1;

        ItemStack toGive = stack.split(countToTake);
        if (stack.isEmpty()) {
            hoveredSession.setStack(hoveredSlot, ItemStack.EMPTY);
        }

        if (client.world != null) {
            BlockState bState = client.world.getBlockState(hoveredSession.getPos());
            if (bState.getBlock() instanceof EnderChestBlock) {
                player.getEnderChestInventory().setStack(hoveredSlot, hoveredSession.getStack(hoveredSlot).copy());
            }
        }

        int sw = client.getWindow().getScaledWidth();
        int sh = client.getWindow().getScaledHeight();
        Vector2f slotPos = hoveredSession.getSlotScreenPos(hoveredSlot);

        int[] countsBefore = new int[36];
        for (int i = 0; i < 36; i++) {
            countsBefore[i] = player.getInventory().getStack(i).getCount();
        }

        player.getInventory().insertStack(toGive.copy());

        int selectedHotbar = player.getInventory().getSelectedSlot();
        int increasedSlot = -1;

        if (player.getInventory().getStack(selectedHotbar).getCount() > countsBefore[selectedHotbar]) {
            increasedSlot = selectedHotbar;
        } else {
            for (int i = 0; i < 9; i++) {
                if (player.getInventory().getStack(i).getCount() > countsBefore[i]) {
                    increasedSlot = i;
                    break;
                }
            }
        }

        Vector2f destPos = (increasedSlot >= 0 && increasedSlot < 9)
                ? getHotbarSlotScreenPos(increasedSlot, sw, sh)
                : new Vector2f(sw * 0.65f, sh - 20f);

        flyingItems.add(new FlyingItem(toGive.copy(), slotPos, destPos, 350, true));
        HoloSoundHelper.playSlotHover();

        MinecraftServer server = client.getServer();
        if (server != null) {
            BlockPos targetPos = hoveredSession.getPos();
            int targetSlot = this.hoveredSlot;
            UUID playerUuid = player.getUuid();

            server.execute(() -> {
                ServerPlayerEntity sPlayer = server.getPlayerManager().getPlayer(playerUuid);
                ServerWorld sWorld = server.getWorld(client.world.getRegistryKey());
                if (sPlayer == null || sWorld == null) return;

                Inventory sContainer = getContainerInventoryDirect(sWorld, targetPos, sPlayer);
                if (sContainer != null && targetSlot < sContainer.size()) {
                    ItemStack realStack = sContainer.getStack(targetSlot);
                    if (!realStack.isEmpty()) {
                        int serverTakeCount = shiftHeld ? realStack.getCount() : 1;
                        ItemStack realGive = realStack.split(serverTakeCount);
                        sContainer.markDirty();

                        boolean inserted = sPlayer.getInventory().insertStack(realGive);
                        if (!inserted) {
                            sPlayer.dropItem(realGive, false);
                        }
                        sPlayer.playerScreenHandler.sendContentUpdates();
                    }
                }
            });
        }
    }

    public static boolean canInsertIntoSlot(net.minecraft.world.World world, BlockState state, int slot, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Block b = state.getBlock();
        if (b instanceof AbstractFurnaceBlock) {

            if (slot == 2) return false;

            if (slot == 1) {
                if (world == null) return true;
                return world.getFuelRegistry().isFuel(stack);
            }

            if (slot == 0) {
                if (world == null) return true;
                var recipeManager = world.getRecipeManager();
                if (recipeManager == null) return true;
                var key = net.minecraft.recipe.RecipePropertySet.FURNACE_INPUT;
                if (b instanceof BlastFurnaceBlock) {
                    key = net.minecraft.recipe.RecipePropertySet.BLAST_FURNACE_INPUT;
                } else if (b instanceof SmokerBlock) {
                    key = net.minecraft.recipe.RecipePropertySet.SMOKER_INPUT;
                }
                var propSet = recipeManager.getPropertySet(key);
                return propSet != null && propSet.canUse(stack);
            }
        }
        return true;
    }

    public void handleRightClick() {
        if (hoveredSession == null || hoveredSlot == -1) return;

        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) return;

        if (hoveredSlot >= hoveredSession.getCachedSize()) return;
        ItemStack currentInSlot = hoveredSession.getStack(hoveredSlot);

        if (!currentInSlot.isEmpty()) {
            boolean isShulker = (currentInSlot.getItem() instanceof BlockItem bi && bi.getBlock() instanceof ShulkerBoxBlock)
                    || currentInSlot.getItem().getTranslationKey().contains("shulker_box");

            if (isShulker) {
                hoveredSession.toggleShulkerPreview(hoveredSlot);
                HoloSoundHelper.playSlotHover();
                return;
            }
        }

        ItemStack clientHeld = player.getMainHandStack();
        if (clientHeld.isEmpty()) return;

        BlockState bState = client.world.getBlockState(hoveredSession.getPos());
        if (!canInsertIntoSlot(client.world, bState, hoveredSlot, clientHeld)) {
            return;
        }

        boolean shiftHeld = isShiftHeld();
        int sw = client.getWindow().getScaledWidth();
        int sh = client.getWindow().getScaledHeight();
        int selectedHotbar = player.getInventory().getSelectedSlot();
        Vector2f fromPos = getHotbarSlotScreenPos(selectedHotbar, sw, sh); 
        Vector2f slotPos = hoveredSession.getSlotScreenPos(hoveredSlot);

        if (currentInSlot.isEmpty()) {
            int countToPlace = shiftHeld ? clientHeld.getCount() : 1;
            ItemStack placed = clientHeld.split(countToPlace);
            hoveredSession.setStack(hoveredSlot, placed.copy());

            if (client.world != null) {
                if (bState.getBlock() instanceof EnderChestBlock) {
                    player.getEnderChestInventory().setStack(hoveredSlot, placed.copy());
                }
            }

            flyingItems.add(new FlyingItem(placed.copy(), fromPos, slotPos, 350, false));
            HoloSoundHelper.playSlotHover();

            MinecraftServer server = client.getServer();
            if (server != null) {
                BlockPos targetPos = hoveredSession.getPos();
                int targetSlot = this.hoveredSlot;
                UUID playerUuid = player.getUuid();

                server.execute(() -> {
                    ServerPlayerEntity sPlayer = server.getPlayerManager().getPlayer(playerUuid);
                    ServerWorld sWorld = server.getWorld(client.world.getRegistryKey());
                    if (sPlayer == null || sWorld == null) return;

                    ItemStack held = sPlayer.getMainHandStack();
                    if (held.isEmpty()) return;

                    if (!canInsertIntoSlot(sWorld, sWorld.getBlockState(targetPos), targetSlot, held)) return;

                    Inventory sContainer = getContainerInventoryDirect(sWorld, targetPos, sPlayer);
                    if (sContainer != null && targetSlot < sContainer.size()) {
                        ItemStack placedServer = held.split(countToPlace);
                        sContainer.setStack(targetSlot, placedServer);
                        sContainer.markDirty();
                        sPlayer.playerScreenHandler.sendContentUpdates();
                    }
                });
            }
        } else if (ItemStack.areItemsAndComponentsEqual(currentInSlot, clientHeld)) {
            int max = currentInSlot.getMaxCount();
            int canAdd = max - currentInSlot.getCount();
            if (canAdd > 0) {
                int toAdd = shiftHeld ? Math.min(canAdd, clientHeld.getCount()) : 1;
                clientHeld.decrement(toAdd);
                currentInSlot.increment(toAdd);

                if (client.world != null) {
                    if (bState.getBlock() instanceof EnderChestBlock) {
                        player.getEnderChestInventory().setStack(hoveredSlot, currentInSlot.copy());
                    }
                }

                ItemStack anim = clientHeld.copyWithCount(toAdd);
                flyingItems.add(new FlyingItem(anim, fromPos, slotPos, 350, false));
                HoloSoundHelper.playSlotHover();

                MinecraftServer server = client.getServer();
                if (server != null) {
                    BlockPos targetPos = hoveredSession.getPos();
                    int targetSlot = this.hoveredSlot;
                    UUID playerUuid = player.getUuid();

                    server.execute(() -> {
                        ServerPlayerEntity sPlayer = server.getPlayerManager().getPlayer(playerUuid);
                        ServerWorld sWorld = server.getWorld(client.world.getRegistryKey());
                        if (sPlayer == null || sWorld == null) return;

                        ItemStack held = sPlayer.getMainHandStack();
                        if (held.isEmpty()) return;

                        Inventory sContainer = getContainerInventoryDirect(sWorld, targetPos, sPlayer);
                        if (sContainer != null && targetSlot < sContainer.size()) {
                            ItemStack inSlot = sContainer.getStack(targetSlot);
                            held.decrement(toAdd);
                            inSlot.increment(toAdd);
                            sContainer.markDirty();
                            sPlayer.playerScreenHandler.sendContentUpdates();
                        }
                    });
                }
            }
        }
    }

    public void sortFocusedSession() {
        HoloSession session = (hoveredSession != null) ? hoveredSession : null;
        if (session == null) {
            for (HoloSession s : activeSessions.values()) {
                if (!s.isClosing()) {
                    session = s;
                    break;
                }
            }
        }
        if (session != null) {
            sortSession(session);
        }
    }

    public void sortSession(HoloSession session) {
        if (session == null || session.isFurnace()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) return;

        int totalSlots = session.getCachedSize();
        if (totalSlots <= 0) return;

        List<ItemStack> stacks = new ArrayList<>();
        for (int i = 0; i < totalSlots; i++) {
            ItemStack s = session.getStack(i);
            if (!s.isEmpty()) {
                stacks.add(s.copy());
            }
        }

        if (stacks.isEmpty()) return;

        List<ItemStack> merged = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) continue;
            for (ItemStack existing : merged) {
                if (ItemStack.areItemsAndComponentsEqual(existing, stack) && existing.getCount() < existing.getMaxCount()) {
                    int canTake = existing.getMaxCount() - existing.getCount();
                    int move = Math.min(canTake, stack.getCount());
                    existing.increment(move);
                    stack.decrement(move);
                    if (stack.isEmpty()) break;
                }
            }
            if (!stack.isEmpty()) {
                merged.add(stack);
            }
        }

        merged.sort((a, b) -> {
            int catA = getItemSortCategory(a);
            int catB = getItemSortCategory(b);
            if (catA != catB) {
                return Integer.compare(catA, catB);
            }

            String regA = Registries.ITEM.getId(a.getItem()).getPath();
            String regB = Registries.ITEM.getId(b.getItem()).getPath();

            if (catA == 0) {
                int tierA = getMaterialTier(regA);
                int tierB = getMaterialTier(regB);
                if (tierA != tierB) {
                    return Integer.compare(tierA, tierB); 
                }
                int subA = getWeaponToolSubIndex(regA);
                int subB = getWeaponToolSubIndex(regB);
                if (subA != subB) {
                    return Integer.compare(subA, subB);
                }
            }

            if (catA == 1) {
                int tierA = getMaterialTier(regA);
                int tierB = getMaterialTier(regB);
                if (tierA != tierB) {
                    return Integer.compare(tierA, tierB); 
                }
                int slotA = getArmorSlotIndex(regA);
                int slotB = getArmorSlotIndex(regB);
                if (slotA != slotB) {
                    return Integer.compare(slotA, slotB);
                }
            }

            if (catA == 2) {
                int minTierA = getMineralTier(regA);
                int minTierB = getMineralTier(regB);
                if (minTierA != minTierB) {
                    return Integer.compare(minTierA, minTierB);
                }
            }

            String nameA = a.getName().getString();
            String nameB = b.getName().getString();
            int nameComp = nameA.compareToIgnoreCase(nameB);
            if (nameComp != 0) {
                return nameComp;
            }
            return Integer.compare(b.getCount(), a.getCount());
        });

        for (int i = 0; i < totalSlots; i++) {
            if (i < merged.size()) {
                session.setStack(i, merged.get(i).copy());
            } else {
                session.setStack(i, ItemStack.EMPTY);
            }
        }

        BlockState bState = client.world.getBlockState(session.getPos());
        if (bState.getBlock() instanceof EnderChestBlock) {
            Inventory clientEnder = player.getEnderChestInventory();
            for (int i = 0; i < totalSlots && i < clientEnder.size(); i++) {
                clientEnder.setStack(i, session.getStack(i).copy());
            }
        }

        MinecraftServer server = client.getServer();
        if (server != null) {
            BlockPos targetPos = session.getPos();
            UUID playerUuid = player.getUuid();
            List<ItemStack> serverStacks = new ArrayList<>();
            for (int i = 0; i < totalSlots; i++) {
                serverStacks.add(session.getStack(i).copy());
            }

            server.execute(() -> {
                ServerPlayerEntity sPlayer = server.getPlayerManager().getPlayer(playerUuid);
                ServerWorld sWorld = server.getWorld(client.world.getRegistryKey());
                if (sPlayer == null || sWorld == null) return;

                Inventory sContainer = getContainerInventoryDirect(sWorld, targetPos, sPlayer);
                if (sContainer != null) {
                    for (int i = 0; i < sContainer.size(); i++) {
                        if (i < serverStacks.size()) {
                            sContainer.setStack(i, serverStacks.get(i).copy());
                        } else {
                            sContainer.setStack(i, ItemStack.EMPTY);
                        }
                    }
                    sContainer.markDirty();
                    sPlayer.playerScreenHandler.sendContentUpdates();
                }
            });
        }

        session.triggerSortAnimation();
        HoloSoundHelper.playHoloSort();
    }

    public static int getMaterialTier(String regId) {
        if (regId.contains("netherite")) return 0;
        if (regId.contains("diamond")) return 1;
        if (regId.contains("iron")) return 2;
        if (regId.contains("golden") || regId.contains("gold")) return 3;
        if (regId.contains("chainmail")) return 4;
        if (regId.contains("stone")) return 5;
        if (regId.contains("wooden") || regId.contains("wood")) return 6;
        if (regId.contains("leather")) return 7;
        return 8;
    }

    public static int getArmorSlotIndex(String regId) {
        if (regId.contains("helmet") || regId.contains("cap")) return 0;
        if (regId.contains("chestplate") || regId.contains("tunic") || regId.contains("elytra")) return 1;
        if (regId.contains("leggings") || regId.contains("pants")) return 2;
        if (regId.contains("boots") || regId.contains("shoes")) return 3;
        return 4;
    }

    public static int getWeaponToolSubIndex(String regId) {
        if (regId.contains("sword")) return 0;
        if (regId.contains("axe") && !regId.contains("pickaxe")) return 1;
        if (regId.contains("pickaxe")) return 2;
        if (regId.contains("shovel")) return 3;
        if (regId.contains("hoe")) return 4;
        if (regId.contains("mace")) return 5;
        if (regId.contains("trident")) return 6;
        if (regId.contains("bow") && !regId.contains("crossbow")) return 7;
        if (regId.contains("crossbow")) return 8;
        if (regId.contains("shield")) return 9;
        if (regId.contains("shears")) return 10;
        if (regId.contains("fishing_rod")) return 11;
        if (regId.contains("flint_and_steel")) return 12;
        return 13;
    }

    public static int getMineralTier(String regId) {
        if (regId.contains("netherite")) return 0;
        if (regId.contains("diamond")) return 1;
        if (regId.contains("emerald")) return 2;
        if (regId.contains("gold")) return 3;
        if (regId.contains("iron")) return 4;
        if (regId.contains("copper")) return 5;
        if (regId.contains("lapis")) return 6;
        if (regId.contains("redstone")) return 7;
        if (regId.contains("coal")) return 8;
        if (regId.contains("quartz")) return 9;
        if (regId.contains("amethyst")) return 10;
        return 11;
    }

    public static int getItemSortCategory(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 999;
        Item item = stack.getItem();
        String regId = Registries.ITEM.getId(item).getPath();

        if (regId.contains("sword") || (regId.contains("axe") && !regId.contains("pickaxe"))
                || regId.contains("pickaxe") || regId.contains("shovel") || regId.contains("hoe")
                || regId.contains("mace") || regId.contains("trident") || regId.contains("bow")
                || regId.contains("shield") || regId.contains("shears") || regId.contains("fishing_rod")
                || regId.contains("flint_and_steel")
                || (stack.contains(DataComponentTypes.TOOL) && !regId.contains("helmet") && !regId.contains("chestplate") && !regId.contains("leggings") && !regId.contains("boots"))) {
            return 0;
        }

        if (regId.contains("helmet") || regId.contains("chestplate") || regId.contains("leggings")
                || regId.contains("boots") || regId.contains("elytra") || regId.contains("cap")
                || regId.contains("tunic") || regId.contains("pants") || regId.contains("shoes")
                ) {
            return 1;
        }

        if (regId.contains("netherite") || regId.contains("diamond") || regId.contains("emerald")
                || regId.contains("gold") || regId.contains("iron") || regId.contains("copper")
                || regId.contains("lapis") || regId.contains("redstone") || regId.contains("coal")
                || regId.contains("quartz") || regId.contains("amethyst") || regId.startsWith("raw_")
                || regId.endsWith("_ingot") || regId.endsWith("_nugget")) {
            return 2;
        }

        if (stack.contains(DataComponentTypes.FOOD)
                || regId.contains("potion") || regId.contains("golden_apple") || regId.contains("carrot")
                || regId.contains("bread") || regId.contains("cooked_") || regId.contains("beef")
                || regId.contains("porkchop") || regId.contains("chicken") || regId.contains("mutton")
                || regId.contains("apple")) {
            return 3;
        }

        if (regId.contains("piston") || regId.contains("observer") || regId.contains("repeater")
                || regId.contains("comparator") || regId.contains("dispenser") || regId.contains("dropper")
                || regId.contains("hopper") || regId.contains("lever") || regId.contains("button")
                || regId.contains("pressure_plate") || regId.contains("rail") || regId.contains("target")
                || regId.contains("daylight_detector") || regId.contains("tripwire_hook")) {
            return 4;
        }

        if (item instanceof BlockItem bi && (bi.getBlock() instanceof ShulkerBoxBlock || bi.getBlock() instanceof ChestBlock || bi.getBlock() instanceof BarrelBlock)) {
            return 5;
        }
        if (regId.contains("torch") || regId.contains("lantern") || regId.contains("bucket")
                || regId.contains("compass") || regId.contains("clock") || regId.contains("bundle")
                || regId.contains("lead") || regId.contains("name_tag") || regId.contains("saddle")) {
            return 5;
        }

        if (item instanceof BlockItem) {
            return 6;
        }

        if (regId.contains("bone") || regId.contains("string") || regId.contains("feather")
                || regId.contains("leather") || regId.contains("gunpowder") || regId.contains("ender_pearl")
                || regId.contains("blaze_rod") || regId.contains("slime_ball") || regId.contains("spider_eye")
                || regId.contains("rotten_flesh") || regId.contains("seed") || regId.contains("wheat")
                || regId.contains("egg")) {
            return 7;
        }

        return 8; 
    }

    public void tick() {
        flyingItems.removeIf(FlyingItem::isFinished);
        if (activeSessions.isEmpty()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) {
            forceCloseAll();
            return;
        }

        if (client.world.getTime() % 4 == 0) {
            for (HoloSession session : activeSessions.values()) {
                if (!session.isClosing() && (session.isFurnace() || session.getCachedSize() <= 9)) {
                    syncSessionFromServer(session);
                }
            }
        }

        for (HoloSession session : activeSessions.values()) {
            BlockPos pos = session.getPos();
            double distSq = player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            if (distSq > 40.0 * 40.0) {
                startClosingSession(session);
            }
        }

        activeSessions.values().removeIf(session -> {
            if (session.isFullyClosed()) {
                closeBarrelBlock(session.getPos());
                if (session.getPartnerPos() != null) {
                    closeBarrelBlock(session.getPartnerPos());
                }
                return true;
            }
            return false;
        });
    }
}
