# Tour materials (October 2026)

## Customer 1.11 accessible restrooms and material detail — 11 October 2026

`WashroomDoors` is the shared floor/hinge/collision catalog: four rooms, each with three toilets, two showers and an entrance. Animated leaves use a small reusable mesh; a conservative swept volume blocks movement during turning and refuses use if the visitor would be hit. Door selection prefers the door being viewed when multiple leaves are nearby. Room entry points appear in navigation and maps. Gaming/billiards rear bounds grow to fit the new facilities; G lounge retains its location with two added showers. The outdoor solid box is removed and the washroom pavilion sits behind the paddock, outside the road/rail/5 m chase-camera envelope.

Original detailed fittings include taps, basins, toilet cisterns, seats, rain heads, mixers, shelves, hooks, drains, vents and fixed TOILET/SHOWER signage. Existing original albedo sheets are reused. Door timber uses local mesh coordinates to avoid sliding or visible repeats during rotation; optional derivative-based surface grain uses the existing texture height approximation. Filtered procedural grass variation and layered rounded tree crowns replace the single-color ground/spherical tree silhouette. Room lamps are toned down after screenshot review. These are approximate real-time materials and static shadow maps; mirrors do not reproduce the exact interior. No new raster assets or user reference photos are packaged.

Both GLES3 and compatibility shader paths are retained. Final capture/test evidence is documented in `VERIFICATION.md`.

## Customer 1.7 reference-style layout and usable controls — 9 October 2026

Gaming is now four orderly six-seat rows with blue-white edge piping, decorated bench end panels, ceiling light panels and contoured upholstered chair backs with genuine harness cutouts. Nine separate private PC rooms have 1, 2 or 4 rigs; `PrivateRoomPlan.kt` shares their capacities/names with migration 005. Gaming bounds extend to 28×52 m and the ground lounge gains a separate wing for four VIP simulator booths. The eight simulator meshes use grooved aluminium profiles, fasteners, fixed seat rails, contoured carbon bucket backs, perforated pedals, shifters, wheel buttons and curved ultrawide displays. User reference photos remain references and are not packaged.

`TourWorld` now maps atlas UVs onto eight rows. The shared atlas remains 2048×2048 POT for GLES2 mipmap compatibility and includes locally drawn carbon fallback, static simulator-screen artwork and ceiling panels. Original independent material/launcher/sky textures remain. Carbon uses pixel-footprint-filtered procedural weave. Lighting adds GGX/Schlick microfacet highlights, a rough environment reflection sampled from the existing panorama, and tighter shadow depth/bias to make furniture cast useful ground shadows. This is an environment approximation and static shadow bake, not scene-exact ray tracing or photographic reconstruction.

All four meshes were rebaked with the new atlas encoding. Small dynamic kart/door meshes use that same encoding. Kart controls use large drawn pedal icons, brake+steer side slip, reverse, and time-based kerb drag with recoverable boundary collisions. Restart/exit are in the top toolbar; restart confirms. Jump is larger. The mini-game remains an offline single-player time trial; racing simulators are bookable physical facilities, not a second driving game.

Small buttons, casters and RGB fan rings use fewer triangles; PC backs keep smooth normals with lower tessellation than the close-view racing shells. Final Gaming/Lounge/Billiards/Outdoor meshes contain 1,321,692 / 315,588 / 206,436 / 165,600 vertices. Matte surfaces skip expensive environment/specular work. Movement, jumping and driving consume elapsed frame time up to 0.5 seconds with collision/physics substeps, and the renderer resets its clock on resume. This corrects slow landing on low-frame-rate renders; it does not establish a frame-rate guarantee on physical devices.

## Customer 1.6 tour experience — 9 October 2026

32 gaming rigs now sit on continuous shared benches (24 PC / 8 VIP), with thin curved displays, articulated arms, complete keyboards, mouse pads, cases/fans, paired light bars, tidy cables and hanging headsets. Chairs use smooth upholstered backs. Wood flooring and neutral light reduce the old blocky neon appearance. Every monitor shares a crisp original 1024×576 launcher texture drawn locally; no game-brand interface is copied. The reference photo remains a visual reference and is not packaged.

Walking/sprinting use collision substeps; jumping uses ballistic gravity and ground landing. The G washroom has a separate wash area plus three cubicles. Dynamic hinged door meshes and open/closed collision share dimensions; closing is refused when it intersects the visitor. Small local dynamic meshes supplement the baked scene without rebuilding a floor during play.

The kart circuit is G / ground level, on a continuous 1 km ground plane with a procedural daylight sky instead of the old high-rise panorama. One Catmull–Rom spline defines the visible 582 m track, curbs, barriers, driving boundary, gates and map. The offline time trial includes driver/chase camera, acceleration/brake, grip/side slip, two-thumb drift, ordered lap gates, times, persistent device-local best, reset and exit. It has no skills, power-ups, opponents or multiplayer. Scene/race state survives activity recreation. The existing four asset filenames are retained and must be rebaked after geometry or plan changes.

Actual final emulator captures use `test-artifacts/CTFLY-test/experience-*.png`. This remains real-time concept geometry rather than photographic reconstruction. No new cloud schema or assets downloaded by the app.

## Customer 1.5 clarity update — 9 October 2026

Default Ultra retains native scene pixels up to a 4096px edge / six-million-pixel budget. Balanced (1920px / two million) and Smooth (1280px / one million) are available through the picture-quality icon; the selection survives recreation. The expand icon hides both destination rows, increasing the scene's usable height. This replaces the earlier fixed 1600px cap.

Material albedo uses independent 2048px texture sheets rather than the small signage cells. Windows and sky use a separate full-image texture. A GLES3 context is preferred, with GLES2 fallback. GLES3 or supported GLES2 extensions use unwrapped world-coordinate derivatives for material mip selection, preventing fract seams from choosing incorrect mip levels. Anisotropic filtering up to 8x is enabled only when supported. Shadows use 2048px where supported. An offscreen native-resolution FXAA pass smooths contrast edges; if its framebuffer cannot be allocated the scene uses direct rendering. Lighting is more neutral and grain contrast is reduced.

New original asset: `app/src/main/res/drawable-nodpi/tour_materials_clean.png`, generated with the built-in imagegen tool. Prompt: "Albedo-only 2 by 2 material sheet: clean warm ivory porcelain stone with subtle mineral variation and no black pores/speckles; restrained straight walnut grain; fine neutral gray billiard felt; fine soft gray leather. Equal quadrants, flat orthographic uniform diffuse light, tileable edges, no labels, baked shadows, reflections, sharpening or perspective." Source sheets remain preserved. Geometry and cloud schema are unchanged; 1.5 requires no new SQL.

The floor is now one continuous surface with pixel-footprint-filtered tile joints. This eliminates hundreds of geometric subpixel gaps that previously appeared as dotted/dirty patterns in the distance, and reduces triangle counts without changing collision or fixture counts. The offscreen scene uses 24-bit depth where supported to reduce thin-surface flicker.

The new materials improve clarity; procedural furniture remains a real-time concept model. Generated material/booking assets are not actual renderer screenshots. Actual emulator captures are under `test-artifacts/CTFLY-test`.

Filtering references: [Khronos texture gradients](https://registry.khronos.org/webgl/extensions/EXT_shader_texture_lod/) and [anisotropic filtering](https://registry.khronos.org/webgl/extensions/EXT_texture_filter_anisotropic/). Extension support is queried at runtime; these references describe the sampling behavior, not device compatibility guarantees.

## Earlier 1.4 implementation

`tour_materials.png` supplies limestone, walnut, billiard cloth and leather. `campus_materials.png` supplies asphalt, brushed metal, rubber and acoustic carpet. Both are original AI-generated texture atlases created with the imagegen skill. Android composes a 4096×2048 runtime atlas (2048×1024 on devices with smaller texture limits). `campus_skyline.png` is original exterior scenery behind windows and in a direction-mapped outdoor sky pass, with no enclosing sky walls. The user's example photograph is a visual reference and is not bundled.

`pool_club.png` and `kart_circuit.png` are original generated concept photos used in the booking page header. They are promotional concept assets, not screenshots of the interactive 3D renderer. Actual renderer screenshots are saved separately under test-artifacts.

All furniture, tubes, pockets, chair frames and architectural geometry are generated locally in Kotlin. The tour remains a fictional CTFLY interior, not a scan of an existing venue. Lighting uses a static filtered shadow map, material textures, smooth surface normals and analytic light contributions; it is not a ray-traced or photographic reconstruction.

The geometry is prebuilt into four `app/src/main/assets/tour/campus-*.bin` files, one each for Gaming (L1), Billiards (L2), Social Lounge (G) and Outdoor. Only the selected floor is loaded. The phone reads packed buffers instead of constructing furniture at runtime. Regenerate after editing `TourWorld.kt` with `./gradlew :app:bakeTourMesh`, **wait for that command to finish, then run the build in a separate Gradle invocation**. Android's task dependencies can merge assets before baking if both are passed together. Tests check exact mesh/source consistency, separated equipment counts, walkable destinations and lift access on every floor; final APK assets are separately compared against the source files.

Shadows bake when a floor/context is loaded. Texture and shader uniform budgets adapt to device limits. Graphics configuration prefers 4× antialiasing and 24-bit depth, with GLES2 fallback. A device without a complete shadow framebuffer renders without cast shadows. The 3D surface is capped at 1600px on its long edge; Compose controls stay at native resolution. Rotation/recreation saves floor, position, yaw and pitch. Floors connect through an elevator selection UI; there is no continuous animated stair traversal between scene files.

The tour works offline. Expanded live booking inventory requires `supabase/004_venue_campus.sql`, which preserves existing IDs/records, tops up facility counts and adds short kart/arcade duration rules. It does not connect an online payment gateway or make the kart tour a driving game.
