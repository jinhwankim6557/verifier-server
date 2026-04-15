package org.omnione.did.base.db.domain;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;


/**
 * QPolicyProfile is a Querydsl query type for PolicyProfile
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QPolicyProfile extends EntityPathBase<PolicyProfile> {

    private static final long serialVersionUID = 224411802L;

    public static final QPolicyProfile policyProfile = new QPolicyProfile("policyProfile");

    public final QBaseEntity _super = new QBaseEntity(this);

    //inherited
    public final DateTimePath<java.time.Instant> createdAt = _super.createdAt;

    public final StringPath description = createString("description");

    public final StringPath encoding = createString("encoding");

    public final NumberPath<Long> filterId = createNumber("filterId", Long.class);

    public final StringPath format = createString("format");

    public final NumberPath<Long> id = createNumber("id", Long.class);

    public final StringPath language = createString("language");

    public final StringPath link = createString("link");

    public final StringPath policyProfileId = createString("policyProfileId");

    public final NumberPath<Long> processId = createNumber("processId", Long.class);

    public final StringPath title = createString("title");

    public final StringPath type = createString("type");

    //inherited
    public final DateTimePath<java.time.Instant> updatedAt = _super.updatedAt;

    public final StringPath value = createString("value");

    public QPolicyProfile(String variable) {
        super(PolicyProfile.class, forVariable(variable));
    }

    public QPolicyProfile(Path<? extends PolicyProfile> path) {
        super(path.getType(), path.getMetadata());
    }

    public QPolicyProfile(PathMetadata metadata) {
        super(PolicyProfile.class, metadata);
    }

}

