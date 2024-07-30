import React, { useEffect, useState } from 'react'
import Header from '../Header/Header'
import './Catalog.css'
import Input from '../Inputs/Input'
import { Box, Button, FormControl, FormControlLabel, FormLabel, IconButton, InputAdornment, InputLabel, OutlinedInput, Pagination, Radio, RadioGroup, Slider, TextField } from '@mui/material'
import { CategoryService } from '../Services/CategoryService'
import { ProductService } from '../Services/ProductService'
import { useForm } from 'react-hook-form'
import Product from '../Product/Product'
import Footer from '../Footer/Footer'
import { SearchBarIcon } from '../../assets/icons'

const minDistance = 100;

export default function Catalog() {

    const [products, setProducts] = useState([])
    const [total, setTotal] = useState(null)
    const [categories, setCategories] = useState(null)
    const { register, handleSubmit, formState: { errors } } = useForm()
    const [filterOptions, setFilterOptions] = useState({
        label: "",
        price: [0, 9999],
        cat: "",
        index: 0,
        size: 12   
    })





    // CALCULATE HOW MANY PAGES EACH TIME
    const calcPages = (numberOfItems, size) => {
        const div10 = numberOfItems / size;
        if(div10 <= 0){
          return 1
        }else if(div10 > 0){
          return Math.ceil(div10)
        }
      }




    // PAGINATION HANDLER
    const handlePagination = (event, page) => {
        setFilterOptions({
            ...filterOptions, index: page-1
        })
    }





    // SLIDER ONCHANGE CALLBACK
    const handleChange = (event, newValue, activeThumb) => {

        // setFilterOptions({...filterOptions, price: newValue})

        if (!Array.isArray(newValue)) {
            return;
          }
      
          if (activeThumb === 0) {
            setFilterOptions({
                ...filterOptions, 
                price: [Math.min(newValue[0], filterOptions.price[1] - minDistance), filterOptions.price[1]]
            })
          } else {
            setFilterOptions({
                ...filterOptions, 
                price: [filterOptions.price[0], Math.max(newValue[1], filterOptions.price[0] + minDistance)]
            })
          }

      };




    // PRODUCTS FETCHER
    function searchProducts(){
        const productLabel = filterOptions.label === "" ? null : filterOptions.label;
        const productCat = filterOptions.cat === "" ? null : filterOptions.cat;
        ProductService.searchProducts(
            productLabel,
            filterOptions.price[0],
            filterOptions.price[1],
            productCat,
            filterOptions.index,
            filterOptions.size
        ).then(
            axRex => setProducts(axRex.data)
        ).catch(
            axErr => console.log(axErr.response.data.details)
        )
    }


    function searchProductsSetTotal(){
        const productLabel = filterOptions.label === "" ? null : filterOptions.label;
        const productCat = filterOptions.cat === "" ? null : filterOptions.cat;
        ProductService.searchProducts(
            productLabel,
            filterOptions.price[0],
            filterOptions.price[1],
            productCat,
            0,
            99999
        ).then(
            axRex => setTotal(axRex.data.length)
        ).catch(
            axErr => console.log(axErr.response.data.details)
        )
    }




    // INITIALIZING CATEGORIES LIST FROM SERVER
    useEffect(() => {
        CategoryService.getCategories()
        .then(
            axRes => setCategories(axRes.data)
        ).catch(
            axErr => console.log(axErr.response.data.details)
        )
    }, [])





    // INITIALIZING PRODUCTS
    useEffect(() => {
        searchProductsSetTotal()
        searchProducts()
    }, [filterOptions])





  return (
    <div className='global_wrapper product_list_page_wrapper'>
        <Header/>
        <div className="product_list_container container header_offset">

            {/* <div className="page_path">
                {window.location.href.replace("http://localhost:5173", "Home").replace("/", " > ")}
            </div> */}

            <div className="products_sorting">

                <div className="product_sorting_search">
                <FormControl sx={{ minWidth: '50ch' }} variant="outlined">
                    <InputLabel sx={{fontSize: "1.5rem"}} htmlFor="outlined-adornment-password">Search...</InputLabel>
                    <OutlinedInput
                        id="outlined-adornment-password"
                        type='text'
                        sx={{fontSize: "1.5rem"}}
                        endAdornment={
                            <InputAdornment position="end">
                                <IconButton
                                    aria-label="toggle password visibility"
                                    edge="end"
                                >
                                {SearchBarIcon}
                                </IconButton>
                            </InputAdornment>
                        }
                        label="Search..."
                        value={filterOptions.label}
                        onChange={(e) => setFilterOptions({
                            ...filterOptions,
                            label: e.target.value
                        })}
                    />
                </FormControl>                    
                </div>

                <div className="product_sorting_size">
                    <span>Size:</span>
                    <select 
                        value={filterOptions.size}
                        onChange={(e) => setFilterOptions({...filterOptions, size: e.target.value})}
                        id="list_size_select"
                        className='product_sorting_size_select'
                    >

                        <option value="12">12</option>
                        <option value="18">18</option>
                        <option value="24">24</option>

                    </select>
                </div>

                {/* <div className="product_sorting_sort"></div> */}

            </div>

            <div className="products_container">

                <div className="products_filter">
                    <FormControl>
                        <FormLabel className='products_filter_category_label products_filter_label'>Category</FormLabel>
                        <RadioGroup
                            aria-labelledby="demo-radio-buttons-group-label"
                            value={filterOptions.cat}
                            onChange={(e) => setFilterOptions({...filterOptions, cat: e.target.value})}
                            
                        >
                            {categories && categories.map(
                                category => <FormControlLabel
                                                key={category.name} 
                                                className='products_filter_category_radio_option'
                                                value={category.name}
                                                control={<Radio />} 
                                                label={category.name} 
                                            />
                            )}
                            <FormControlLabel 
                                className='products_filter_category_radio_option'
                                value={""}
                                control={<Radio />} 
                                label={"ALL"} 
                            />

                        </RadioGroup>
                    </FormControl>
                    <FormControl>
                        <FormLabel className='products_filter_label'>Price</FormLabel>
                        <Box className={"products_filter_price_container"} sx={{ width: 300 }}>
                            <Box className={"products_filter_price_range"}>
                                <TextField
                                    className='products_filter_min_price price_input'
                                    id="outlined-basic" 
                                    label="Min price" 
                                    variant="outlined"
                                    type='number'
                                    value={filterOptions.price[0]}
                                    onChange={e => {
                                        setFilterOptions({
                                            ...filterOptions, 
                                            price: [
                                                Math.min(e.target.value, filterOptions.price[1] - minDistance), 
                                                Number.parseInt(filterOptions.price[1])
                                            ]
                                        })
                                    }}
                                    InputProps={{
                                        startAdornment: 
                                            <InputAdornment 
                                                className='products_filter_min_price_dollar price_input_dollar' 
                                                position='start'
                                            >
                                                $
                                            </InputAdornment>
                                    }}
                                />
                                <TextField
                                    className='products_filter_max_price price_input'
                                    id="outlined-basic" 
                                    label="Max price" 
                                    variant="outlined"
                                    type='number'
                                    value={filterOptions.price[1]}
                                    onChange={e => {

                                        setFilterOptions({
                                            ...filterOptions, 
                                            price: [
                                                Number.parseInt(filterOptions.price[0]), 
                                                Math.max(e.target.value, filterOptions.price[0] + minDistance)
                                            ]
                                        })
                                    }}
                                    InputProps={{
                                        startAdornment: <InputAdornment className='products_filter_max_price_dollar price_input_dollar' position='start'>$</InputAdornment>
                                    }}
                                />
                            </Box>
                            <Slider
                                value={filterOptions.price}
                                onChange={handleChange}
                                valueLabelDisplay="auto"
                                // getAriaValueText={valuetext}
                                disableSwap
                                color='info'
                                max={12000}
                                step={100}
                            />
                        </Box>
                    </FormControl>

                </div>

                <div className="products_list_container">
                    {
                        products.length > 0 ? (
                            <div className="products_list">
                                {products && products.map(
                                    product => <Product
                                        key={product.id}
                                        id={product.id}
                                        image={product.image}
                                        label={product.label}
                                        owner={product.owner}
                                        price={product.price}
                                        stock={product.stock}
                                        averageRating={product.averageRating}
                                        reviewsCount={product.reviewsCount}
                                    />
                                )}
                            </div>
                        ) : (
                            <div className='products_list_noresult'>No results match your search</div>
                        )
                    }
                    <Pagination 
                        count={calcPages(total, filterOptions.size)}
                        onChange={handlePagination}
                        size='large'
                        showLastButton
                        showFirstButton
                        />
                </div>

            </div>
        </div>
        <Footer/>
    </div>
  )
}
