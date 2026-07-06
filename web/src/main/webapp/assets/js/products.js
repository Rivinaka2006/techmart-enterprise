
document.addEventListener("DOMContentLoaded", function () {
    "use strict";

    const API_BASE = (window.getTmApiBase ? window.getTmApiBase() : "/techmart-web/api");
    let categoryLookup = {};
    let editingProductId = null;

    const ATTRIBUTE_TEMPLATES = [
        {
            match: ["smartphone", "phone", "mobile"],
            fields: [
                field("ram", "RAM", "8GB"),
                field("storage", "Storage", "256GB"),
                field("color", "Color", "Black"),
                field("screenSize", "Screen Size", "6.5 inch"),
                field("battery", "Battery", "5000mAh"),
                field("camera", "Camera", "48MP")
            ]
        },
        {
            match: ["laptop", "computer", "electronics"],
            fields: [
                field("processor", "Processor", "Intel Core i5"),
                field("ram", "RAM", "16GB"),
                field("storage", "Storage", "512GB SSD"),
                field("display", "Display", "15.6 inch"),
                field("graphics", "Graphics", "Integrated"),
                field("color", "Color", "Silver")
            ]
        },
        {
            match: ["apparel", "clothing", "fashion"],
            fields: [
                field("size", "Size", "M"),
                field("color", "Color", "Blue"),
                field("material", "Material", "Cotton"),
                field("gender", "Gender", "Unisex"),
                field("fit", "Fit", "Regular")
            ]
        },
        {
            match: ["home", "kitchen"],
            fields: [
                field("material", "Material", "Stainless steel"),
                field("color", "Color", "Black"),
                field("dimensions", "Dimensions", "30 x 20 x 10 cm"),
                field("capacity", "Capacity", "2L"),
                field("warranty", "Warranty", "1 year")
            ]
        },
        {
            match: ["sport", "fitness"],
            fields: [
                field("size", "Size", "Standard"),
                field("color", "Color", "Black"),
                field("material", "Material", "Rubber"),
                field("weight", "Weight", "1kg"),
                field("sportType", "Sport Type", "Fitness")
            ]
        },
        {
            match: ["grocery", "food"],
            fields: [
                field("weight", "Weight", "500g"),
                field("flavor", "Flavor", "Original"),
                field("expiry", "Expiry", "2027-12-31"),
                field("ingredients", "Ingredients", "List ingredients"),
                field("storage", "Storage Instructions", "Store in a cool dry place")
            ]
        }
    ];

    const DEFAULT_ATTRIBUTE_FIELDS = [
        field("color", "Color", "Black"),
        field("size", "Size", "Standard"),
        field("material", "Material", "Mixed"),
        field("warranty", "Warranty", "1 year")
    ];

    function parseJsonResponse(response) {
        return response.text().then(function (text) {
            if (!response.ok) {
                var contentType = response.headers.get("content-type") || "";
                var message = "Request failed: " + response.status + " " + response.statusText;
                if (contentType.indexOf("application/json") !== -1) {
                    try {
                        var body = JSON.parse(text);
                        if (body && body.error) message += " - " + body.error;
                    } catch (e) {
                        
                    }
                } else if (text.trim().startsWith("<")) {
                    message += " - server returned HTML instead of JSON";
                } else if (text.trim()) {
                    message += " - " + text.trim().slice(0, 120);
                }
                throw new Error(message);
            }
            if (!text) return null;
            try {
                return JSON.parse(text);
            } catch (err) {
                var snippet = text.trim().slice(0, 200);
                var hint = snippet.startsWith("<") ? "HTML" : "invalid JSON";
                throw new Error("Invalid JSON response from " + response.url + " (" + hint + "): " + snippet);
            }
        });
    }

    function loadProducts() {
        return fetch(API_BASE + "/products")
            .then(parseJsonResponse)
            .then(function (products) {
                const container = document.getElementById("productsTableBody");
                if (!container) return;

                updateSummaryCards(products || []);
                container.innerHTML = "";

                (products || []).forEach(function (product) {
                    const brandName = product.brand && product.brand.brandName ? product.brand.brandName : "";
                    const categoryName = product.category && product.category.categoryName ? product.category.categoryName : "";
                    let specs = {};
                    try {
                        specs = JSON.parse(product.attributes || "{}");
                    } catch (e) {
                        console.error("Error parsing attributes for product: " + product.name);
                    }

                    const specsHtml = Object.keys(specs)
                        .map(function (key) {
                            return '<span class="badge bg-dark text-light me-1">' +
                                escapeHtml(key.toUpperCase()) + ": " + escapeHtml(specs[key]) +
                                "</span>";
                        })
                        .join(" ");
                    const addToCartAction = getAddToCartAction(product);

                    const row = `
                        <tr>
                            <td class="cell-id">#${escapeHtml(product.id)}</td>
                            <td class="cell-main">
                                ${escapeHtml(product.name)}
                                <div class="cell-sub">${escapeHtml(brandName)} &middot; ${escapeHtml(categoryName)}</div>
                                ${specsHtml}
                            </td>
                            <td>${escapeHtml(categoryName)}</td>
                            <td class="mono">${formatCurrency(product.price)}</td>
                            <td class="mono">${product.quantity != null ? product.quantity : 0}</td>
                            <td>${getProductStatusBadge(product.status)}</td>
                            <td>
                                <div class="row-actions">
                                    ${addToCartAction}
                                    <button class="btn-icon-tm" title="Edit" onclick="editProduct(${product.id})"><i class="bi bi-pencil"></i></button>
                                </div>
                            </td>
                        </tr>
                    `;
                    container.innerHTML += row;
                });
            })
            .catch(function (error) {
                console.error("Error fetching products:", error);
            });
    }

    function loadProductMeta() {
        return fetch(API_BASE + "/products/meta")
            .then(parseJsonResponse)
            .then(function (meta) {
                categoryLookup = {};
                (meta.categories || []).forEach(function (category) {
                    categoryLookup[String(category.id)] = category.categoryName;
                });
                populateSelect("productBrandInput", meta.brands || [], "id", "brandName", "Select brand");
                populateSelect("productCategoryInput", meta.categories || [], "id", "categoryName", "Select category");
                renderAttributeFields("");
            })
            .catch(function (error) {
                console.error("Error fetching product metadata:", error);
                populateSelect("productBrandInput", [], "id", "brandName", "Unable to load brands");
                populateSelect("productCategoryInput", [], "id", "categoryName", "Unable to load categories");
            });
    }

    function populateSelect(id, items, valueKey, labelKey, placeholder) {
        const select = document.getElementById(id);
        if (!select) return;
        select.innerHTML = '<option value="">' + escapeHtml(placeholder) + "</option>";
        items.forEach(function (item) {
            const option = document.createElement("option");
            option.value = item[valueKey];
            option.textContent = item[labelKey];
            select.appendChild(option);
        });
    }

    function field(name, label, placeholder) {
        return { name: name, label: label, placeholder: placeholder };
    }

    function attributeFieldsForCategory(categoryName) {
        const normalized = normalizeCategoryName(categoryName);
        const template = ATTRIBUTE_TEMPLATES.find(function (item) {
            return item.match.some(function (keyword) {
                return normalized.indexOf(keyword) !== -1;
            });
        });
        return template ? template.fields : DEFAULT_ATTRIBUTE_FIELDS;
    }

    function renderAttributeFields(categoryId) {
        const container = document.getElementById("productAttributeFields");
        if (!container) return;

        const categoryName = categoryLookup[String(categoryId || "")];
        if (!categoryName) {
            container.innerHTML = '<div class="col-12"><div class="form-hint">Select a category to load related product fields.</div></div>';
            return;
        }

        const fields = attributeFieldsForCategory(categoryName);
        container.innerHTML = fields.map(function (item) {
            return `
                <div class="col-md-6">
                    <label class="form-label-tm" for="attr_${escapeHtml(item.name)}">${escapeHtml(item.label)}</label>
                    <input class="form-control-tm" id="attr_${escapeHtml(item.name)}" name="attr_${escapeHtml(item.name)}" data-attribute-key="${escapeHtml(item.name)}" placeholder="${escapeHtml(item.placeholder)}">
                </div>
            `;
        }).join("");
    }

    function buildAttributesJson() {
        const attributes = {};
        document.querySelectorAll("[data-attribute-key]").forEach(function (input) {
            const key = input.getAttribute("data-attribute-key");
            const value = input.value.trim();
            if (key && value) attributes[key] = value;
        });
        return JSON.stringify(attributes);
    }

    function openAddProductModal() {
        editingProductId = null;
        const form = document.getElementById("addProductForm");
        if (form) form.reset();
        setProductModalMode("add");
        showFormError("");
        loadProductMeta().then(function () {
            const categorySelect = document.getElementById("productCategoryInput");
            if (categorySelect) renderAttributeFields(categorySelect.value);
            const modalEl = document.getElementById("addProductModal");
            if (modalEl && window.bootstrap) {
                bootstrap.Modal.getOrCreateInstance(modalEl).show();
            }
        });
    }

    function openEditProductModal(productId) {
        editingProductId = productId;
        const form = document.getElementById("addProductForm");
        if (form) form.reset();
        setProductModalMode("edit");
        showFormError("");

        Promise.all([
            loadProductMeta(),
            fetch(API_BASE + "/products/" + encodeURIComponent(productId)).then(parseJsonResponse)
        ]).then(function (results) {
            fillProductForm(results[1]);
            const modalEl = document.getElementById("addProductModal");
            if (modalEl && window.bootstrap) {
                bootstrap.Modal.getOrCreateInstance(modalEl).show();
            }
        }).catch(function (error) {
            console.error("Error loading product for edit:", error);
            alert(error.message || "Failed to load product details.");
            editingProductId = null;
        });
    }

    function fillProductForm(product) {
        const form = document.getElementById("addProductForm");
        if (!form || !product) return;

        const fields = form.elements;
        fields.namedItem("name").value = product.name || "";
        fields.namedItem("price").value = product.price != null ? product.price : "";
        fields.namedItem("brandId").value = product.brand && product.brand.id ? product.brand.id : "";
        fields.namedItem("categoryId").value = product.category && product.category.id ? product.category.id : "";
        fields.namedItem("status").value = product.status || "ACTIVE";

        renderAttributeFields(fields.namedItem("categoryId").value);
        fillAttributeFields(product.attributes);
    }

    function fillAttributeFields(attributesJson) {
        let attributes = {};
        try {
            attributes = JSON.parse(attributesJson || "{}");
        } catch (e) {
            attributes = {};
        }

        document.querySelectorAll("[data-attribute-key]").forEach(function (input) {
            const key = input.getAttribute("data-attribute-key");
            if (Object.prototype.hasOwnProperty.call(attributes, key)) {
                input.value = attributes[key];
            }
        });
    }

    function setProductModalMode(mode) {
        const isEdit = mode === "edit";
        setText("addProductModalLabel", isEdit ? "Edit Product" : "Add Product");
        setText("productModalSubtitle", isEdit ? "Update product details in the live catalog" : "Create a product in the live catalog");
        const submitBtn = document.getElementById("addProductSubmitBtn");
        if (submitBtn) {
            submitBtn.innerHTML = isEdit ? '<i class="bi bi-check2"></i> Save Changes' : '<i class="bi bi-check2"></i> Submit';
        }
    }

    function submitProduct(event) {
        event.preventDefault();
        const form = event.currentTarget;
        const fields = form.elements;
        const submitBtn = document.getElementById("addProductSubmitBtn");

        const payload = {
            name: fields.namedItem("name").value.trim(),
            brandId: Number(fields.namedItem("brandId").value),
            categoryId: Number(fields.namedItem("categoryId").value),
            price: Number(fields.namedItem("price").value),
            status: fields.namedItem("status").value,
            attributes: buildAttributesJson()
        };

        if (!payload.name || !payload.brandId || !payload.categoryId || Number.isNaN(payload.price)) {
            showFormError("Product name, brand, category, and price are required.");
            return;
        }

        if (submitBtn) submitBtn.disabled = true;
        showFormError("");

        const url = editingProductId ? API_BASE + "/products/" + encodeURIComponent(editingProductId) : API_BASE + "/products";
        const method = editingProductId ? "PUT" : "POST";

        fetch(url, {
            method: method,
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload)
        })
            .then(parseJsonResponse)
            .then(function () {
                const modalEl = document.getElementById("addProductModal");
                if (modalEl && window.bootstrap) {
                    bootstrap.Modal.getOrCreateInstance(modalEl).hide();
                }
                editingProductId = null;
                loadProducts();
            })
            .catch(function (error) {
                console.error("Error saving product:", error);
                showFormError(error.message || "Failed to save product.");
            })
            .finally(function () {
                if (submitBtn) submitBtn.disabled = false;
            });
    }

    function getProductStatusBadge(status) {
        const normalized = (status || "ACTIVE").toString().toUpperCase();
        const labelMap = {
            ACTIVE: "Active",
            INACTIVE: "Inactive",
            OUT_OF_STOCK: "Out of Stock",
            DISCONTINUED: "Discontinued"
        };
        const classMap = {
            ACTIVE: "success",
            INACTIVE: "secondary",
            OUT_OF_STOCK: "warning",
            DISCONTINUED: "danger"
        };

        const label = labelMap[normalized] || normalized.replace(/_/g, " ").replace(/\b\w/g, function (char) { return char.toUpperCase(); });
        const badgeClass = classMap[normalized] || "secondary";
        return '<span class="badge-tm badge-tm-' + badgeClass + '">' + escapeHtml(label) + "</span>";
    }

    function getAddToCartAction(product) {
        const quantity = Number(product && product.quantity != null ? product.quantity : 0);
        const status = normalizeStatus(product && product.status);
        const isAvailable = status === "ACTIVE" && quantity > 0;

        if (isAvailable) {
            return '<button class="btn-icon-tm" title="Add to cart" onclick="addToCart(' +
                Number(product.id) + ')"><i class="bi bi-cart-plus"></i></button>';
        }

        const reason = status !== "ACTIVE" ? "Inactive products cannot be added to cart" : "Out of stock";
        return '<button class="btn-icon-tm" title="' + escapeHtml(reason) +
            '" disabled><i class="bi bi-cart-x"></i></button>';
    }

    function normalizeStatus(status) {
        return (status || "").toString().trim().toUpperCase().replace(/\s+/g, "_");
    }

    function isActiveListing(product) {
        const status = normalizeStatus(product && product.status);
        const quantity = Number(product && product.quantity != null ? product.quantity : 0);
        const activeStatuses = ["ACTIVE", "AVAILABLE", "IN_STOCK", "LISTED"];
        return activeStatuses.indexOf(status) !== -1 && quantity > 0;
    }

    function updateSummaryCards(products) {
        const totalProducts = Array.isArray(products) ? products.length : 0;
        const activeListings = (products || []).filter(isActiveListing).length;
        const lowStock = (products || []).filter(function (product) {
            const quantity = Number(product && product.quantity != null ? product.quantity : 0);
            return quantity >= 1 && quantity < 10;
        }).length;

        setText("totalProductsValue", totalProducts);
        setText("activeListingsValue", activeListings);
        setText("lowStockValue", lowStock);
        setText("activeListingsTrend", (totalProducts ? Math.round((activeListings / totalProducts) * 100) : 0) + "% of catalog");
        setText("totalProductsTrend", totalProducts + " total SKUs");
        setText("lowStockTrend", lowStock + " need reorder");
    }

    function showFormError(message) {
        const error = document.getElementById("addProductError");
        if (!error) return;
        error.textContent = message || "";
        error.classList.toggle("d-none", !message);
    }

    function normalizeCategoryName(value) {
        return String(value || "").toLowerCase().replace(/&/g, "and");
    }

    function formatCurrency(value) {
        return "LKR " + Number(value || 0).toLocaleString(undefined, {
            minimumFractionDigits: 2,
            maximumFractionDigits: 2
        });
    }

    function setText(id, value) {
        const el = document.getElementById(id);
        if (el) el.textContent = value;
    }

    function escapeHtml(value) {
        return String(value == null ? "" : value)
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;")
            .replace(/'/g, "&#039;");
    }

    const addProductBtn = document.getElementById("addProductBtn");
    if (addProductBtn) addProductBtn.addEventListener("click", openAddProductModal);

    const addProductForm = document.getElementById("addProductForm");
    if (addProductForm) addProductForm.addEventListener("submit", submitProduct);

    const categorySelect = document.getElementById("productCategoryInput");
    if (categorySelect) {
        categorySelect.addEventListener("change", function () {
            renderAttributeFields(categorySelect.value);
        });
    }

    window.editProduct = openEditProductModal;

    loadProducts();
});
